package io.xpipe.app.core.mode;

import io.xpipe.app.action.AbstractAction;
import io.xpipe.app.action.ActionProvider;
import io.xpipe.app.beacon.AppBeaconServer;
import io.xpipe.app.beacon.BlobManager;
import io.xpipe.app.beacon.mcp.AppMcpServer;
import io.xpipe.app.browser.BrowserFullSessionModel;
import io.xpipe.app.browser.file.BrowserLocalFileSystem;
import io.xpipe.app.browser.icon.BrowserIconManager;
import io.xpipe.app.core.*;
import io.xpipe.app.core.check.*;
import io.xpipe.app.core.window.AppDialog;
import io.xpipe.app.core.window.AppMainWindow;
import io.xpipe.app.core.window.AppWindowTitle;
import io.xpipe.app.ext.DataStoreProviders;
import io.xpipe.app.ext.ProcessControlProvider;
import io.xpipe.app.ext.StartOnInitStore;
import io.xpipe.app.hub.comp.StoreFilterState;
import io.xpipe.app.hub.comp.StoreQuickConnect;
import io.xpipe.app.hub.comp.StoreViewState;
import io.xpipe.app.icon.SystemIconManager;
import io.xpipe.app.issue.TrackEvent;
import io.xpipe.app.platform.PlatformInit;
import io.xpipe.app.platform.PlatformState;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.sessions.SessionRecorder;
import io.xpipe.app.prefs.WorkspaceManager;
import io.xpipe.app.process.LocalShell;
import io.xpipe.app.pwman.KeePassXcPasswordManager;
import io.xpipe.app.storage.DataStorage;
import io.xpipe.app.storage.DataStorageSyncHandler;
import io.xpipe.app.terminal.TerminalDockHubManager;
import io.xpipe.app.terminal.TerminalLauncherManager;
import io.xpipe.app.terminal.TerminalView;
import io.xpipe.app.update.UpdateAvailableDialog;
import io.xpipe.app.update.UpdateChangelogDialog;
import io.xpipe.app.update.UpdateNagDialog;
import io.xpipe.app.util.*;
import io.xpipe.core.XPipeDaemonMode;

import java.util.concurrent.CountDownLatch;

public class AppBaseMode extends AppOperationMode {

    private boolean initialized;

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getId() {
        return "background";
    }

    @Override
    public void onSwitchTo() {
        if (initialized) {
            return;
        }

        // For debugging error handling
        // if (true) throw new IllegalStateException();

        TrackEvent.info("Initializing base mode components ...");
        AppCertStore.init();
        AppMainWindow.loadingText("checkingLicense");
        LicenseProvider.get().init();
        AppMainWindow.loadingText("initializingApp");
        AppWindowTitle.init();
        AppPathCorruptCheck.check();
        AppWindowsTempCheck.check();
        AppDirectoryPermissionsCheck.checkDirectory(AppSystemInfo.ofCurrent().getTemp());
        WindowsRegistry.init();
        AppAvCheck.check();
        AppJavaOptionsCheck.check();
        AppHardwareAccelerationDisableCheck.check();
        AppSid.init();
        AppBeaconServer.init();
        AppLayoutModel.init();

        if (AppOperationMode.getStartupMode() == XPipeDaemonMode.GUI) {
            AppPtbDialog.showIfNeeded();
        }

        // If we downloaded an update, and decided to no longer automatically update, don't remind us!
        // You can still update manually in the about tab
        if (AppPrefs.get().automaticallyUpdate().get()
                || AppPrefs.get().checkForSecurityUpdates().get()) {
            UpdateAvailableDialog.showIfNeeded(true);
        } else {
            UpdateNagDialog.showAndWaitIfNeeded();
        }

        var imagesLoaded = new CountDownLatch(1);
        var iconsInit = new CountDownLatch(1);
        var iconsLoaded = new CountDownLatch(1);
        var browserLoaded = new CountDownLatch(1);
        var shellLoaded = new CountDownLatch(1);
        var storageLoaded = new CountDownLatch(1);
        var localPrefsLoaded = new CountDownLatch(1);
        var syncPrefsLoaded = new CountDownLatch(1);
        ThreadHelper.load(
                true,
                () -> {
                    // Wrapped: shell init may fail when ProcessControlProvider is absent (stub proc
                    // module). Latches must always count down to avoid deadlocking downstream threads.
                    try {
                        AppShellCheck.check();
                        LocalShell.init();
                    } catch (UnsupportedOperationException e) {
                        TrackEvent.warn("Shell initialization skipped: " + e.getMessage());
                    }
                    shellLoaded.countDown();
                    AppRosettaCheck.check();
                    AppWindowsArmCheck.check();
                    AppTestCommandCheck.check();
                    // This might be slow on macOS and might take longer than the platform init.
                    // Wrapped: initDefaultValues may throw when ProcessControlProvider is absent.
                    // localPrefsLoaded MUST always count down to avoid deadlocking Thread 3.
                    try {
                        AppPrefs.get().initDefaultValues();
                        WorkspaceManager.init();
                    } catch (Throwable t) {
                        TrackEvent.warn("Prefs/workspace init partially failed (stub proc build): " + t.getMessage());
                    }
                    localPrefsLoaded.countDown();
                    PlatformInit.init(true);
                    TrackEvent.info("Shell initialization thread completed");
                },
                () -> {
                    shellLoaded.await();
                    // DataStorageSyncHandler requires ProcessControlProvider; skip sync when absent.
                    if (ProcessControlProvider.get() != null) {
                        DataStorageSyncHandler.getInstance().init();
                        if (DataStorageSyncHandler.getInstance().supportsSync()) {
                            AppMainWindow.loadingText("loadingGpg");
                            DataStorageSyncHandler.getInstance().prepareGpgIfNeeded();
                            AppMainWindow.loadingText("loadingGit");
                        }
                        DataStorageSyncHandler.getInstance().retrieveSyncedData();
                    } else {
                        TrackEvent.warn("Skipping data storage sync: ProcessControlProvider not available");
                    }
                    AppMainWindow.loadingText("loadingSettings");
                    // Wrapped: initSynced calls loadSharedRemote → LocalShell.getShell() which
                    // throws when ProcessControlProvider absent. syncPrefsLoaded MUST count down.
                    try {
                        AppPrefs.initSynced();
                    } catch (Throwable t) {
                        TrackEvent.warn("Skipping synced prefs load (stub proc build): " + t.getMessage());
                    }
                    syncPrefsLoaded.countDown();
                    AppMainWindow.loadingText("loadingConnections");
                    // Wrapped: DataStorage.init / initStorage may also fail without local shell.
                    // storageLoaded MUST count down to avoid deadlocking Thread 5 and 6.
                    try {
                        DataStorage.init();
                        AppPrefs.initStorage();
                    } catch (Throwable t) {
                        TrackEvent.warn("Skipping storage init (stub proc build): " + t.getMessage());
                    }
                    storageLoaded.countDown();
                    AppMcpServer.init();
                    iconsInit.await();
                    StoreFilterState.init();
                    StoreViewState.init();
                    // Wrapped: StoreQuickConnect requires SSH/provider extensions not available in stub
                    // proc builds. Non-fatal — quick-connect shortcut is disabled, core UI still works.
                    try {
                        StoreQuickConnect.init();
                    } catch (Throwable t) {
                        TrackEvent.warn("StoreQuickConnect skipped (stub proc build): " + t.getMessage());
                    }
                    AppMainWindow.loadingText("loadingSettings");
                    TrackEvent.info("Connection storage initialization thread completed");
                },
                () -> {
                    PlatformInit.init(true);
                    imagesLoaded.await();
                    browserLoaded.await();
                    iconsLoaded.await();
                    localPrefsLoaded.await();
                    AppMainWindow.loadingText("loadingUserInterface");
                    AppMainWindow.initContent();
                    TrackEvent.info("Window content initialization thread completed");
                },
                () -> {
                    AppFileWatcher.init();
                    FileBridge.init();
                    BlobManager.init();
                    TerminalView.init();
                    TerminalLauncherManager.init();
                    TerminalDockHubManager.init();
                    RemoteDesktopWindow.init();
                    SessionRecorder.init();
                    TrackEvent.info("File/Terminal initialization thread completed");
                },
                () -> {
                    PlatformInit.init(true);
                    AppImages.init();
                    imagesLoaded.countDown();
                    SystemIconManager.init();
                    syncPrefsLoaded.await();
                    SystemIconManager.initAdditional();
                    iconsInit.countDown();
                    storageLoaded.await();
                    SystemIconManager.prepareUsedIconImages();
                    iconsLoaded.countDown();
                    TrackEvent.info("Platform initialization thread completed");
                },
                () -> {
                    BrowserIconManager.init();
                    shellLoaded.await();
                    // Wrapped: BrowserLocalFileSystem/BrowserFullSessionModel need local shell.
                    // browserLoaded MUST count down to avoid deadlocking Thread 3.
                    try {
                        BrowserLocalFileSystem.init();
                        storageLoaded.await();
                        BrowserFullSessionModel.init();
                    } catch (Throwable t) {
                        TrackEvent.warn("Browser file system skipped (stub proc build): " + t.getMessage());
                    }
                    browserLoaded.countDown();
                    TrackEvent.info("Browser initialization thread completed");
                });

        // AppGreetingsDialog.showAndWaitIfNeeded();
        TrackEvent.info("Waiting for startup dialogs to close");
        AppDialog.waitForAllDialogsClose();
        UpdateChangelogDialog.showIfNeeded();

        ActionProvider.initProviders();
        DataStoreProviders.init();
        StartOnInitStore.init();

        AppConfigurationDialog.showIfNeeded();

        TrackEvent.info("Finished base components initialization");
        initialized = true;
    }

    @Override
    public void onSwitchFrom() {}

    @Override
    public void finalTeardown() throws Exception {
        TrackEvent.withInfo("Base mode shutdown started").build();
        AbstractAction.reset();
        AppMcpServer.reset();
        WorkspaceManager.reset();
        RemoteDesktopWindow.reset();
        AppPrefs.reset();
        DataStorage.reset();
        if (ProcessControlProvider.get() != null) {
            DataStorageSyncHandler.getInstance().reset();
        }
        SshLocalBridge.reset();
        BrowserFullSessionModel.DEFAULT.reset();
        LocalShell.reset(false);
        BrowserLocalFileSystem.reset();
        if (ProcessControlProvider.get() != null) {
            ProcessControlProvider.get().reset();
        }
        AppBeaconServer.reset();
        KeePassXcPasswordManager.reset();
        StoreViewState.reset();
        StoreFilterState.reset();
        AppLayoutModel.reset();
        AppTheme.reset();
        PlatformState.teardown();
        DataStoreProviders.reset();
        AppResources.reset();
        AppExtensionManager.reset();
        AppDataLock.unlock();
        BlobManager.reset();
        FileBridge.reset();
        AppCertStore.reset();
        AppFileWatcher.reset();
        GlobalTimer.reset();
        LocalFileTracker.reset();
        TrackEvent.info("Base mode shutdown finished");
    }
}
