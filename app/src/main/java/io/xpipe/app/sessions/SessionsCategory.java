package io.xpipe.app.sessions;

import io.xpipe.app.comp.BaseRegionBuilder;
import io.xpipe.app.platform.LabelGraphic;
import io.xpipe.app.platform.OptionsBuilder;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.prefs.AppPrefsCategory;

/**
 * Preferences page for the session artifacts subsystem — screen recording for
 * RDP/VNC, CLI transcription, and the shared retention budget.
 *
 * <p>Every toggle defaults OFF. Recording and transcription are independent —
 * either may be on without the other.
 */
public class SessionsCategory extends AppPrefsCategory {

    @Override
    protected String getId() {
        return "sessions";
    }

    @Override
    protected LabelGraphic getIcon() {
        return new LabelGraphic.IconGraphic("mdi2v-video-vintage");
    }

    @Override
    protected BaseRegionBuilder<?, ?> create() {
        var prefs = AppPrefs.get();

        return new OptionsBuilder()
                .title("sessionsConfiguration")
                .sub(new OptionsBuilder()
                        .nameAndDescription("sessionRecording")
                        .addToggle(prefs.sessionRecording))
                .sub(new OptionsBuilder()
                        .nameAndDescription("sessionTranscription")
                        .addToggle(prefs.sessionTranscription))
                .sub(new OptionsBuilder()
                        .nameAndDescription("sessionRetentionDays")
                        .addInteger(prefs.sessionRetentionDays))
                .sub(new OptionsBuilder()
                        .nameAndDescription("sessionMaxMegabytes")
                        .addInteger(prefs.sessionMaxMegabytes))
                .buildComp();
    }
}
