package io.xpipe.app.sessions;

/**
 * Granularity of CLI session transcription written under
 * {@code ~/.bastion/sessions/{id}/}.
 *
 * <ul>
 *   <li>{@link #OFF} — no transcription artifacts written. The wrapper is not
 *       constructed at all; zero overhead.</li>
 *   <li>{@link #COMMANDS} — one JSONL line per executed command in
 *       {@code commands.jsonl} (timestamp, command, exit code).</li>
 *   <li>{@link #FULL} — full asciinema cast v2 transcript including all stdout
 *       / stderr. Reserved for a future milestone; treat as equivalent to
 *       {@link #COMMANDS} until that lands.</li>
 * </ul>
 */
public enum TranscriptionMode {
    OFF,
    COMMANDS,
    FULL;

    public boolean enabled() {
        return this != OFF;
    }
}
