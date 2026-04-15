package com.classic.preservitory.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes the binary sprite index file ({@code sprites.idx}).
 *
 * <h3>File format</h3>
 * <pre>
 *   4 bytes  — magic bytes: 'S' 'I' 'D' 'X'
 *   2 bytes  — format version (big-endian short, currently 1)
 *   4 bytes  — entry count   (big-endian int)
 *   per entry:
 *     2 bytes  — UTF-8 id byte length (big-endian short, unsigned)
 *     N bytes  — sprite id (UTF-8)
 *     8 bytes  — byte offset in sprites.dat (big-endian long)
 *     4 bytes  — byte length in sprites.dat (big-endian int)
 *     2 bytes  — image width  in pixels (big-endian short, unsigned)
 *     2 bytes  — image height in pixels (big-endian short, unsigned)
 * </pre>
 *
 * Sprite IDs must be ≤ 65 535 bytes in UTF-8 and images must be ≤ 65 535 px
 * wide/tall (both well beyond practical limits).
 */
public final class SpriteIndex {

    private static final byte[] MAGIC   = { 'S', 'I', 'D', 'X' };
    private static final short  VERSION = 1;

    private SpriteIndex() {}

    // -----------------------------------------------------------------------
    //  Write
    // -----------------------------------------------------------------------

    /**
     * Writes {@code entries} to {@code indexPath} in the SIDX binary format.
     *
     * @param indexPath destination file path (will be created or overwritten)
     * @param entries   sprites to index (order is preserved)
     * @throws IOException on any I/O error
     */
    public static void write(Path indexPath, List<SpriteEntry> entries) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(indexPath)))) {
            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeInt(entries.size());
            for (SpriteEntry e : entries) {
                byte[] idBytes = e.id.getBytes(StandardCharsets.UTF_8);
                out.writeShort(idBytes.length);
                out.write(idBytes);
                out.writeLong(e.offset);
                out.writeInt(e.length);
                out.writeShort(e.width);
                out.writeShort(e.height);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Read
    // -----------------------------------------------------------------------

    /**
     * Reads the SIDX index from {@code indexPath}.
     *
     * @param indexPath source file
     * @return unmodifiable list of sprite entries in file order
     * @throws IOException if the file is missing, corrupt, or uses an unsupported version
     */
    public static List<SpriteEntry> read(Path indexPath) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(indexPath)))) {

            // Validate magic
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'S' || magic[1] != 'I' || magic[2] != 'D' || magic[3] != 'X') {
                throw new IOException("Not a sprite index file (wrong magic): " + indexPath);
            }

            // Validate version
            short version = in.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported sprite index version " + version
                        + " (expected " + VERSION + "): " + indexPath);
            }

            int count = in.readInt();
            List<SpriteEntry> entries = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                int    idLen   = in.readShort() & 0xFFFF;
                byte[] idBytes = new byte[idLen];
                in.readFully(idBytes);
                String id     = new String(idBytes, StandardCharsets.UTF_8);
                long   offset = in.readLong();
                int    length = in.readInt();
                int    width  = in.readShort() & 0xFFFF;
                int    height = in.readShort() & 0xFFFF;
                entries.add(new SpriteEntry(id, offset, length, width, height));
            }

            return Collections.unmodifiableList(entries);
        }
    }
}
