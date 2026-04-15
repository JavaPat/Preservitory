package com.classic.preservitory.cache;

/**
 * Immutable descriptor for a single sprite stored in the packed cache.
 *
 * Contains the sprite's lookup ID, its byte range inside {@code sprites.dat},
 * and its pixel dimensions (stored in the index so callers can query size
 * without decoding the image data).
 */
public final class SpriteEntry {

    /** Lookup key used by {@link SpriteCache#getSprite(String)}. */
    public final String id;

    /** Byte offset of this sprite's PNG data inside {@code sprites.dat}. */
    public final long offset;

    /** Byte length of this sprite's PNG data. */
    public final int length;

    /** Image width in pixels. */
    public final int width;

    /** Image height in pixels. */
    public final int height;

    public SpriteEntry(String id, long offset, int length, int width, int height) {
        this.id     = id;
        this.offset = offset;
        this.length = length;
        this.width  = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return "SpriteEntry{id='" + id + "', offset=" + offset
                + ", length=" + length + ", w=" + width + ", h=" + height + '}';
    }
}
