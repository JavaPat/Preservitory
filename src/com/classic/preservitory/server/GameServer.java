package com.classic.preservitory.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Authoritative game server for Preservitory multiplayer.
 *
 * One thread per connected client (ClientHandler) receives position updates and
 * marks the server state dirty.  A single dedicated broadcast thread fires at a
 * fixed 20 Hz regardless of how many clients are connected, so N clients sending
 * at 20 Hz each no longer produce N×20 broadcasts per second.
 *
 * === Protocol (text lines, UTF-8) ===
 *   Server → Client on connect : "WELCOME <id>"
 *   Client → Server            : "UPDATE <id> <x> <y>"
 *   Server → ALL clients       : "PLAYERS <id1> <x1> <y1>;<id2> <x2> <y2>;..."
 *   Server → ALL clients       : "DISCONNECT <id>"
 *
 * === Broadcast timing ===
 *   Client sends    : ~20 Hz  (50 ms interval, throttled in ClientConnection)
 *   Server receives : up to N × 20 Hz (N = number of clients)
 *   Server sends    : exactly 20 Hz  (50 ms fixed timer, only if state changed)
 *
 * === Usage ===
 *   java com.classic.preservitory.server.GameServer
 *   — or —
 *   java com.classic.preservitory.Main --server
 */
public class GameServer {

    private static final int  PORT            = 5555;
    /** How often the broadcast thread sends a PLAYERS snapshot (milliseconds). */
    private static final long BROADCAST_MS    = 50;   // 20 Hz

    // -----------------------------------------------------------------------
    //  Shared state
    // -----------------------------------------------------------------------

    /** Current position of every connected player. */
    private final ConcurrentHashMap<String, int[]> positions = new ConcurrentHashMap<>();

    /** All active client handlers (CopyOnWriteArrayList — safe for concurrent read). */
    private final List<ClientHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * Dirty flag: set to true whenever any position changes.
     * The broadcast thread resets it to false after each send so we never push
     * an unchanged snapshot.
     */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** Monotonically incrementing counter for unique player IDs. */
    private int nextId = 1;

    // -----------------------------------------------------------------------
    //  Start
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[Server] Listening on port " + PORT
                + "  (broadcast rate: " + (1000 / BROADCAST_MS) + " Hz)");

        startBroadcastThread();

        //noinspection InfiniteLoopStatement
        while (true) {
            Socket socket = serverSocket.accept();
            String id = "P" + (nextId++);
            System.out.println("[Server] Player connected: " + id
                    + " from " + socket.getInetAddress().getHostAddress());

            // Register with a default position before the client sends UPDATE
            positions.put(id, new int[]{0, 0});

            ClientHandler handler = new ClientHandler(socket, id, this);
            handlers.add(handler);
            new Thread(handler, "client-" + id).start();
        }
    }

    // -----------------------------------------------------------------------
    //  Fixed-rate broadcast thread
    // -----------------------------------------------------------------------

    /**
     * Runs a daemon thread that sends the full PLAYERS snapshot to all clients
     * at a fixed 20 Hz, but only when the state has changed since the last send.
     *
     * Decoupling broadcast rate from receive rate prevents N-clients × send-rate
     * flooding and gives the server a predictable outbound message cadence.
     */
    private void startBroadcastThread() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(BROADCAST_MS);
                    // Only send if something actually changed
                    if (dirty.compareAndSet(true, false)) {
                        broadcastPositions();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "server-broadcaster");
        t.setDaemon(true);   // don't prevent JVM exit
        t.start();
    }

    // -----------------------------------------------------------------------
    //  Called by ClientHandler threads
    // -----------------------------------------------------------------------

    /**
     * Store the new position and mark state dirty.
     * Does NOT broadcast immediately — the broadcast thread handles that.
     */
    void updatePosition(String id, int x, int y) {
        positions.put(id, new int[]{x, y});
        dirty.set(true);
    }

    /**
     * Remove a player, notify all remaining clients immediately (no 50 ms delay),
     * and push a fresh snapshot so ghost players vanish without waiting for the
     * next scheduled broadcast.
     */
    void removePlayer(String playerId) {
        positions.remove(playerId);
        handlers.removeIf(h -> h.getId().equals(playerId));
        System.out.println("[Server] Player disconnected: " + playerId
                + " (" + positions.size() + " players online)");

        // Immediate broadcast on disconnect — don't wait for the timer
        broadcastAll("DISCONNECT " + playerId);
        broadcastPositions();
        dirty.set(false);   // we just sent a fresh snapshot; suppress duplicate
    }

    // -----------------------------------------------------------------------
    //  Broadcast helpers
    // -----------------------------------------------------------------------

    /**
     * Build and send a full PLAYERS snapshot to every connected handler.
     * Format: "PLAYERS id1 x1 y1;id2 x2 y2;..."
     */
    private void broadcastPositions() {
        if (positions.isEmpty()) return;

        StringBuilder sb = new StringBuilder("PLAYERS");
        for (Map.Entry<String, int[]> e : positions.entrySet()) {
            sb.append(' ').append(e.getKey())
              .append(' ').append(e.getValue()[0])
              .append(' ').append(e.getValue()[1])
              .append(';');
        }
        broadcastAll(sb.toString());
    }

    /**
     * Broadcast a chat message from one player to ALL connected clients
     * (including the sender, so their chat appears on all screens).
     *
     * Format sent to clients: "CHAT <fromId> <message>"
     */
    void broadcastChat(String fromId, String message) {
        // Always filter on the server — never trust client input.
        // A modified client could bypass the client-side check.
        String clean = com.classic.preservitory.util.ChatFilter.filter(message);
        if (clean == null) {
            System.out.println("[Chat] Blocked invalid message from " + fromId);
            return;   // drop silently; no broadcast
        }
        System.out.println("[Chat] " + fromId + ": " + clean);
        broadcastAll("CHAT " + fromId + " " + clean);
    }

    /** Send a raw message line to every connected handler. */
    private void broadcastAll(String message) {
        for (ClientHandler h : handlers) {
            h.send(message);
        }
    }

    // -----------------------------------------------------------------------
    //  Entry point (standalone mode)
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    // -----------------------------------------------------------------------
    //  Inner class: one instance per connected client
    // -----------------------------------------------------------------------

    /**
     * Handles all socket I/O for one client on its own thread.
     * Parses UPDATE messages and delegates state changes to GameServer.
     */
    private static final class ClientHandler implements Runnable {

        private final Socket     socket;
        private final String     id;
        private final GameServer server;
        private PrintWriter      out;   // initialised in run()

        ClientHandler(Socket socket, String id, GameServer server) {
            this.socket = socket;
            this.id     = id;
            this.server = server;
        }

        String getId() { return id; }

        /** Thread-safe: send one line to this client (no-op if not yet ready). */
        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

                // auto-flush so every println flushes immediately
                out = new PrintWriter(socket.getOutputStream(), true);

                // Greet the client with its assigned ID
                out.println("WELCOME " + id);

                String line;
                while ((line = in.readLine()) != null) {
                    processMessage(line.trim());
                }

            } catch (IOException e) {
                // Socket closed — treat as normal disconnect
            } finally {
                server.removePlayer(id);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        /** Parse and dispatch a single message from the client. */
        private void processMessage(String line) {
            if (line.isEmpty()) return;

            String[] parts = line.split(" ");

            if (parts.length == 4 && "UPDATE".equals(parts[0])) {
                // "UPDATE <id> <x> <y>"
                try {
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);
                    server.updatePosition(id, x, y);
                } catch (NumberFormatException ignored) {
                    System.err.println("[Server] Malformed UPDATE from " + id + ": " + line);
                }

            } else if (parts.length >= 2 && "CHAT".equals(parts[0])) {
                // "CHAT <message text>" — message can contain spaces
                // Strip the "CHAT " prefix (5 chars) to get the raw message
                String message = line.length() > 5 ? line.substring(5) : "";
                if (!message.isEmpty()) {
                    server.broadcastChat(id, message);
                }
            }
        }
    }
}
