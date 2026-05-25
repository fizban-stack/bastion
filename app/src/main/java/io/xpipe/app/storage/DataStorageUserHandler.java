package io.xpipe.app.storage;

import io.xpipe.app.comp.BaseRegionBuilder;
import io.xpipe.app.ext.ProcessControlProvider;
import io.xpipe.app.platform.OptionsBuilder;
import io.xpipe.app.prefs.VaultAuthentication;

import javafx.beans.property.ObjectProperty;

import java.io.IOException;
import javax.crypto.SecretKey;

public interface DataStorageUserHandler {

    static DataStorageUserHandler getInstance() {
        // ProcessControlProvider absent in stub proc builds — return no-op stub.
        if (ProcessControlProvider.get() == null) {
            return NoOpStorageUserHandler.INSTANCE;
        }
        return (DataStorageUserHandler) ProcessControlProvider.get().getStorageUserHandler();
    }

    int getUserCount();

    void init() throws IOException;

    void save();

    void login();

    SecretKey getEncryptionKey();

    BaseRegionBuilder<?, ?> createOverview();

    OptionsBuilder createGroupStrategyOptions(ObjectProperty<DataStorageGroupStrategy> groupStrategy);

    String getActiveUser();

    VaultAuthentication getVaultAuthenticationType();

    DataStorageGroupStrategy getGroupStrategy(String user);

    void setCurrentGroupStrategy(DataStorageGroupStrategy groupStrategy);
}
