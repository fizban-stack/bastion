package io.xpipe.ext.uacc;

import io.xpipe.app.comp.BaseRegionBuilder;
import io.xpipe.app.util.LicensedFeature;
import io.xpipe.app.util.LicenseProvider;
import io.xpipe.app.util.LicenseRequiredException;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;

/**
 * Free, open-source implementation of LicenseProvider for Bastion.
 *
 * All features are perpetually supported. No license server, no paywall,
 * no trial periods. This replaces the commercial uacc module from upstream xpipe.
 */
public class FreeLicenseProvider extends LicenseProvider {

    @Override
    public void updateDate(String date) {
        // No-op — no license date tracking in the free build
    }

    @Override
    public String formatExceptionMessage(String name, boolean plural, LicensedFeature licensedFeature) {
        return "Feature '" + name + "' is supported in this build of Bastion.";
    }

    @Override
    public String getLicenseId() {
        return "free";
    }

    @Override
    public ObservableValue<String> licenseTitle() {
        return new SimpleStringProperty("");
    }

    @Override
    public LicensedFeature getFeature(String id) {
        return new FreeLicensedFeature(id);
    }

    @Override
    public LicensedFeature checkOsName(String name) {
        return new FreeLicensedFeature(name);
    }

    @Override
    public void checkOsNameOrThrow(String s) {
        // No-op — all OS names are supported
    }

    @Override
    public void showLicenseAlert(LicenseRequiredException ex) {
        // No-op — no license alerts in the free build
    }

    @Override
    public void init() {
        // No-op — no license initialization required
    }

    @Override
    public BaseRegionBuilder<?, ?> overviewPage() {
        // Return null — AppLayoutComp filters out entries with null comp
        return null;
    }

    @Override
    public boolean hasPaidLicense() {
        return false;
    }

    @Override
    public boolean shouldReportError() {
        return true;
    }
}
