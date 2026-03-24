package com.classic.preservitory.system;

import javax.sound.sampled.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates and plays simple sound effects using only the Java standard library.
 * No audio files required — all sounds are synthesized as PCM byte arrays at
 * startup and stored as pre-loaded {@link Clip}s for zero-latency playback.
 *
 * Sounds are generated once in the constructor.  Call {@link #play(Sound)} from
 * the game loop; it is safe to call from any thread.  Failures (e.g. headless
 * environment, no audio device) are silently ignored — the game continues
 * without sound rather than crashing.
 *
 * Toggle sound on/off with {@link #setEnabled(boolean)} or the M key in-game.
 */
public class SoundSystem {

    // -----------------------------------------------------------------------
    //  Sound catalogue
    // -----------------------------------------------------------------------

    public enum Sound {
        CHOP,           // axe hitting wood — short noise burst
        MINE,           // pickaxe on rock  — lower noise burst
        HIT,            // combat hit       — short low tone
        LEVEL_UP,       // ascending C-E-G arpeggio
        ITEM_PICKUP     // high blip on loot collection
    }

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    private static final float  SAMPLE_RATE  = 44_100f;   // Hz
    private static final int    BITS         = 8;
    private static final int    CHANNELS     = 1;         // mono
    private static final boolean SIGNED      = true;
    private static final boolean BIG_ENDIAN  = false;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private final Map<Sound, Clip> clips = new EnumMap<>(Sound.class);
    private volatile boolean enabled = true;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public SoundSystem() {
        AudioFormat fmt = new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, SIGNED, BIG_ENDIAN);

        tryLoad(Sound.CHOP,        fmt, generateNoise(0.09, 0.65));
        tryLoad(Sound.MINE,        fmt, generateNoise(0.14, 0.50));
        tryLoad(Sound.HIT,         fmt, generateTone(180, 0.10, 0.60));
        tryLoad(Sound.LEVEL_UP,    fmt, generateAscending());
        tryLoad(Sound.ITEM_PICKUP, fmt, generateTone(900, 0.06, 0.45));
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Play a sound effect.  If the same sound is already playing it restarts
     * from the beginning (important for rapid combat hits).
     * Does nothing if sound is disabled or if the audio clip failed to load.
     */
    public void play(Sound sound) {
        if (!enabled) return;
        Clip clip = clips.get(sound);
        if (clip == null) return;

        // Stop + rewind + start — all happen synchronously so the sound fires immediately
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    /** Enable or disable all sound output. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Whether sound is currently enabled. */
    public boolean isEnabled() { return enabled; }

    // -----------------------------------------------------------------------
    //  PCM generation
    // -----------------------------------------------------------------------

    /**
     * White-noise burst with a linear amplitude decay.
     * Sounds like a dull thud or hit — useful for chop/mine.
     *
     * @param durationSec length in seconds
     * @param loudness    peak amplitude in [0, 1]
     */
    private static byte[] generateNoise(double durationSec, double loudness) {
        int n = (int)(SAMPLE_RATE * durationSec);
        byte[] buf = new byte[n];
        for (int i = 0; i < n; i++) {
            double envelope = 1.0 - (double) i / n;   // linear decay
            double sample   = (Math.random() * 2 - 1) * 127 * loudness * envelope;
            buf[i] = (byte) Math.round(sample);
        }
        return buf;
    }

    /**
     * Pure sine-wave tone with a linear amplitude decay.
     *
     * @param freqHz      frequency in Hz
     * @param durationSec length in seconds
     * @param loudness    peak amplitude in [0, 1]
     */
    private static byte[] generateTone(double freqHz, double durationSec, double loudness) {
        int n = (int)(SAMPLE_RATE * durationSec);
        byte[] buf = new byte[n];
        for (int i = 0; i < n; i++) {
            double envelope = 1.0 - (double) i / n;
            double sample   = Math.sin(2 * Math.PI * freqHz * i / SAMPLE_RATE)
                              * 127 * loudness * envelope;
            buf[i] = (byte) Math.round(sample);
        }
        return buf;
    }

    /**
     * Three-note ascending arpeggio (C5 → E5 → G5).
     * Each note fades out independently before the next begins.
     */
    private static byte[] generateAscending() {
        double[] freqs    = { 523.25, 659.25, 783.99 }; // C5, E5, G5
        double   noteSec  = 0.13;
        int      noteLen  = (int)(SAMPLE_RATE * noteSec);
        byte[]   buf      = new byte[noteLen * freqs.length];

        for (int n = 0; n < freqs.length; n++) {
            for (int i = 0; i < noteLen; i++) {
                double envelope = 1.0 - (double) i / noteLen;
                double sample   = Math.sin(2 * Math.PI * freqs[n] * i / SAMPLE_RATE)
                                  * 127 * 0.70 * envelope;
                buf[n * noteLen + i] = (byte) Math.round(sample);
            }
        }
        return buf;
    }

    // -----------------------------------------------------------------------
    //  Clip loading
    // -----------------------------------------------------------------------

    private void tryLoad(Sound sound, AudioFormat fmt, byte[] data) {
        try {
            DataLine.Info info = new DataLine.Info(Clip.class, fmt);
            if (!AudioSystem.isLineSupported(info)) return;

            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(fmt, data, 0, data.length);
            clips.put(sound, clip);
        } catch (Exception ignored) {
            // Sound unavailable — fail silently, game runs without audio
        }
    }
}
