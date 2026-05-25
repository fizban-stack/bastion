package io.xpipe.app.storage;

import java.nio.file.Path;
import java.util.List;

/**
 * No-op stub used when ProcessControlProvider is absent (open-source / stub-proc builds).
 * All sync operations are disabled: supportsSync() returns false so StandardStorage skips
 * all sync calls in save/load paths.
 */
class NoOpStorageSyncHandler implements DataStorageSyncHandler {

    static final NoOpStorageSyncHandler INSTANCE = new NoOpStorageSyncHandler();

    @Override
    public void pullManually() {}

    @Override
    public void pushManually() {}

    @Override
    public void reset() throws Exception {}

    @Override
    public boolean validateConnection() {
        return false;
    }

    @Override
    public boolean supportsSync() {
        return false;
    }

    @Override
    public boolean hasExternalStoredCredentials() {
        return false;
    }

    @Override
    public void init() {}

    @Override
    public void prepareGpgIfNeeded() {}

    @Override
    public void retrieveSyncedData() {}

    @Override
    public void refreshRemoteData() {}

    @Override
    public void afterStorageLoad() {}

    @Override
    public void beforeStorageSave() {}

    @Override
    public void afterStorageSave(boolean pushIfNeeded, boolean dispose) {}

    @Override
    public void handleEntry(DataStoreEntry entry, boolean exists, boolean dirty) {}

    @Override
    public void handleCategory(DataStoreCategory category, boolean exists, boolean dirty) {}

    @Override
    public void handleDeletion(Path target, String name) {}

    @Override
    public Path getDirectory() {
        return null;
    }

    @Override
    public List<Path> getSavedDataFiles() {
        return List.of();
    }

    @Override
    public Path getDataFile(Path rel) {
        return null;
    }

    @Override
    public Path addDataFile(Path file, Path target, boolean perUser) {
        return null;
    }
}
