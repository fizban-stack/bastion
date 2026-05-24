package io.xpipe.ext.uacc;

import io.xpipe.app.util.LicensedFeature;
import io.xpipe.app.util.LicenseRequiredException;

import javafx.beans.value.ObservableValue;

import java.util.Optional;

/**
 * A free, always-supported implementation of LicensedFeature.
 * Every feature is considered supported — no license checks, no paywalls.
 */
public class FreeLicensedFeature implements LicensedFeature {

    private final String id;
    private final String displayName;

    public FreeLicensedFeature(String id) {
        this.id = id;
        this.displayName = id;
    }

    @Override
    public Optional<String> getDescriptionSuffix() {
        return Optional.empty();
    }

    @Override
    public ObservableValue<String> suffixObservable(ObservableValue<String> s) {
        return s;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean isPlural() {
        return false;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean supportsFeatureInPreview() {
        return false;
    }

    @Override
    public boolean recentlySupportedFeatureInPreview() {
        return false;
    }

    @Override
    public void throwIfUnsupported() throws LicenseRequiredException {
        // Always supported — never throws
    }
}
