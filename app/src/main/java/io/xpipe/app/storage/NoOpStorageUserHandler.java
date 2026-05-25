package io.xpipe.app.storage;

import io.xpipe.app.comp.BaseRegionBuilder;
import io.xpipe.app.comp.RegionBuilder;
import io.xpipe.app.platform.OptionsBuilder;
import io.xpipe.app.prefs.VaultAuthentication;

import javafx.beans.property.ObjectProperty;

import javax.crypto.SecretKey;
import java.io.IOException;

/**
 * No-op stub used when ProcessControlProvider is absent (open-source / stub-proc builds).
 * Provides safe defaults so DataStorage and DataStorageNode can initialize without a live
 * local shell. All methods are either no-ops or return values that disable encryption/sync.
 */
class NoOpStorageUserHandler implements DataStorageUserHandler {

    static final NoOpStorageUserHandler INSTANCE = new NoOpStorageUserHandler();

    @Override
    public int getUserCount() {
        return 0;
    }

    @Override
    public void init() throws IOException {}

    @Override
    public void save() {}

    @Override
    public void login() {}

    @Override
    public SecretKey getEncryptionKey() {
        return null;
    }

    @Override
    public BaseRegionBuilder<?, ?> createOverview() {
        // Return an empty region builder — no user management UI in stub proc builds.
        return RegionBuilder.empty();
    }

    @Override
    public OptionsBuilder createGroupStrategyOptions(ObjectProperty<DataStorageGroupStrategy> groupStrategy) {
        // Return an empty options builder — no group strategy UI in stub proc builds.
        return new OptionsBuilder();
    }

    @Override
    public String getActiveUser() {
        return null;
    }

    @Override
    public VaultAuthentication getVaultAuthenticationType() {
        return VaultAuthentication.USER;
    }

    @Override
    public DataStorageGroupStrategy getGroupStrategy(String user) {
        return null;
    }

    @Override
    public void setCurrentGroupStrategy(DataStorageGroupStrategy groupStrategy) {}
}
