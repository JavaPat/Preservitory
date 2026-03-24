package com.classic.preservitory.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the TCP connection between a game client and the Preservitory server.
 *
 * A background daemon thread handles all incoming data; the game loop calls
 * {@link #sendPosition} and reads {@link #getRemotePlayers} from the main thread.
 * The ConcurrentHashMap makes those cross-thread accesses safe without locking.
 *
 * === Protocol handled here ===
 *   Receives "WELCOME <id>"         → stores local player id
 *   Receives "PLAYERS id x y;..."   → updates remote-player snapshot
 *   Receives "DISCONNECT <id>"      → removes a player from the map
 *   Sends    "UPDATE <id> <x> <y>"  → position update, throttled to ~20 Hz
 *
 * === Send throttle ===
 *   Position updates are sent at most once every SEND_INTERVAL_MS (50 ms = 20 Hz)
 *   AND only when the position has actually changed.  This matches the server's
 *   broadcast cadence so we don't push data the server won't forward for another
 *   50 ms anyway.
 *
 * === Update-rate tracking ===
 *   Each PLAYERS message arrival timestamp is fed into an exponential moving
 *   average so callers can display the effective incoming update rate (Hz).
 */
public class ClientConnection {

    private static final String HOST             = "localhost";
    private static final int    PORT             = 5555;
    /** Minimum milliseconds between outgoing position updates (20 Hz). */
    private static final long   SEND_INTERVAL_MS = 50;

    // -----------------------------------------------------------------------
    //  Socket / writer state
    // -----------------------------------------------------------------------

    private Socket      socket;
    private PrintWriter out;

    /** True while the background thread has a live connection. */
    private volatile boolean connected = false;

    /** Server-assigned ID for the local player (null until WELCOME received). */
    private volatile String myId = null;

    // -----------------------------------------------------------------------
    //  Remote-player snapshot (accessed from both threads)
    // -----------------------------------------------------------------------

    /** Latest known positions of all OTHER players: id → [worldX, worldY]. */
    private final ConcurrentHashMap<String, int[]> remotePlayers = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    //  Outgoing throttle state (only accessed from game thread via sendPosition)
    // -----------------------------------------------------------------------

    private int  lastSentX    = Integer.MIN_VALUE;
    private int  lastSentY    = Integer.MIN_VALUE;
    private long lastSentTime = 0;

    // -----------------------------------------------------------------------
    //  Update-rate tracking (written by background thread, read by game thread)
    // -----------------------------------------------------------------------

    /**
     * Exponential moving average of the incoming PLAYERS update rate (Hz).
     * Updated every time a PLAYERS message arrives.
     * 0.0 until the first two messages arrive.
     */
    private volatile double updatesPerSecond = 0.0;

    /** Timestamp of the previous PLAYERS message (for rate computation). */
    private long lastPlayersMsgMs = 0;

    // -----------------------------------------------------------------------
    //  Optional listener
    // -----------------------------------------------------------------------

    /** Callbacks for connection-lifecycle events and incoming chat. */
    public interface Listener {
        /** Called (on the background thread) once WELCOME is received. */
        void onConnected(String assignedId);
        /** Called (on the background thread) when the socket closes. */
        void onDisconnected();
        /**
         * Called (on the background thread) when a CHAT message arrives.
         *
         * @param fromId  Server-assigned ID of the player who sent the message
         * @param message The chat text (everything after the sender ID)
         */
        default void onChat(String fromId, String message) {}
    }

    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    // -----------------------------------------------------------------------
    //  Connect
    // -----------------------------------------------------------------------

    /**
     * Start the background reader thread.
     * Returns immediately.  If the server is unreachable the error is printed
     * and the game continues in single-player mode (all public methods become
     * no-ops or return empty results).
     */
    public void connect() {
        Thread t = new Thread(this::connectAndListen, "net-client-reader");
        t.setDaemon(true);
        t.start();
    }

    // -----------------------------------------------------------------------
    //  Background reader loop
    // -----------------------------------------------------------------------

    private void connectAndListen() {
        try {
            socket = new Socket();
            // 3-second timeout — fail fast if no server
            socket.connect(new InetSocketAddress(HOST, PORT), 3000);
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            System.out.println("[Client] Connected to " + HOST + ":" + PORT);

            BufferedReader in =
                    new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[Client] Could not connect to server: " + e.getMessage()
                    + " — running in single-player mode.");
        } finally {
            connected = false;
            updatesPerSecond = 0.0;
            remotePlayers.clear();
            if (listener != null) listener.onDisconnected();
        }
    }

    // -----------------------------------------------------------------------
    //  Incoming message dispatch (background thread)
    // -----------------------------------------------------------------------

    private void handleMessage(String line) {
        if (line.startsWith("WELCOME ")) {
            myId = line.substring(8).trim();
            System.out.println("[Client] Assigned player ID: " + myId);
            if (listener != null) listener.onConnected(myId);

        } else if (line.startsWith("PLAYERS ")) {
            trackUpdateRate();
            parsePlayersMessage(line.substring(8));

        } else if (line.startsWith("DISCONNECT ")) {
            String id = line.substring(11).trim();
            remotePlayers.remove(id);
            System.out.println("[Client] Player left: " + id);

        } else if (line.startsWith("CHAT ")) {
            // Format: "CHAT <fromId> <message text>"
            String rest     = line.substring(5);   // strip "CHAT "
            int    spaceIdx = rest.indexOf(' ');
            if (spaceIdx > 0 && listener != null) {
                String fromId  = rest.substring(0, spaceIdx);
                String message = rest.substring(spaceIdx + 1);
                listener.onChat(fromId, message);
            }
        }
    }

    /**
     * Measure the interval since the last PLAYERS message and feed it into an
     * exponential moving average (α = 0.2) to compute updates-per-second.
     *
     * EMA smooths out occasional late or bunched deliveries so the debug
     * display doesn't flicker.
     */
    private void trackUpdateRate() {
        long now = System.currentTimeMillis();
        if (lastPlayersMsgMs > 0) {
            double dtSec = (now - lastPlayersMsgMs) / 1000.0;
            if (dtSec > 0.001) {
                double instantHz = 1.0 / dtSec;
                // α = 0.2: slow-moving average — stable display
                updatesPerSecond = updatesPerSecond * 0.8 + instantHz * 0.2;
            }
        }
        lastPlayersMsgMs = now;
    }

    /**
     * Parse "id1 x1 y1;id2 x2 y2;..." and atomically replace the snapshot.
     * Our own ID is excluded so we only track others.
     */
    private void parsePlayersMessage(String payload) {
        Map<String, int[]> updated = new HashMap<>();

        for (String entry : payload.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(" ");
            if (parts.length != 3) continue;
            try {
                String id = parts[0];
                int    x  = Integer.parseInt(parts[1]);
                int    y  = Integer.parseInt(parts[2]);
                if (id.equals(myId)) continue;   // skip our own echo
                updated.put(id, new int[]{x, y});
            } catch (NumberFormatException ignored) { /* skip bad token */ }
        }

        remotePlayers.clear();
        remotePlayers.putAll(updated);
    }

    // -----------------------------------------------------------------------
    //  Outgoing position update (called from game loop / game thread)
    // -----------------------------------------------------------------------

    /**
     * Send a chat message to the server.
     * The server broadcasts it to all other connected clients as
     * {@code "CHAT <ourId> <message>"}.
     *
     * <p>The caller should display the message locally for instant feedback;
     * this method only sends to the server for relay to others.
     *
     * @param message  The text to send (should already be trimmed / validated)
     */
    public void sendChatMessage(String message) {
        if (!connected || myId == null || out == null) return;
        if (message == null || message.isEmpty()) return;
        // Protocol: client sends "CHAT <message>"; server prepends our id when broadcasting
        out.println("CHAT " + message);
    }

    /**
     * Send our current world-pixel position to the server.
     *
     * Skips the send if:
     *   - not connected yet, or
     *   - less than SEND_INTERVAL_MS has elapsed since the last send, or
     *   - the position hasn't changed.
     *
     * @param x  Player world-pixel X (not iso screen X)
     * @param y  Player world-pixel Y (not iso screen Y)
     */
    public void sendPosition(int x, int y) {
        if (!connected || myId == null || out == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSentTime < SEND_INTERVAL_MS) return;   // rate limit
        if (x == lastSentX && y == lastSentY)       return;   // no change

        out.println("UPDATE " + myId + " " + x + " " + y);
        lastSentX    = x;
        lastSentY    = y;
        lastSentTime = now;
    }

    // -----------------------------------------------------------------------
    //  Getters (safe to call from game thread)
    // -----------------------------------------------------------------------

    /**
     * Snapshot of all OTHER players.
     * Keys: server-assigned IDs.  Values: [worldX, worldY].
     * Never includes the local player.
     */
    public Map<String, int[]> getRemotePlayers() {
        return Collections.unmodifiableMap(remotePlayers);
    }

    /** Server-assigned ID for the local player, or null if not yet connected. */
    public String getMyId() { return myId; }

    /** True while the background socket is live. */
    public boolean isConnected() { return connected; }

    /**
     * Effective rate at which PLAYERS snapshots are arriving from the server.
     * Updated via exponential moving average; 0.0 until two messages arrive.
     * Safe to read from the game thread (volatile field).
     */
    public double getUpdatesPerSecond() { return updatesPerSecond; }
}
