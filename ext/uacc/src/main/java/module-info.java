module io.xpipe.ext.uacc {
    requires io.xpipe.app;
    requires javafx.base;

    exports io.xpipe.ext.uacc;

    provides io.xpipe.app.util.LicenseProvider
            with io.xpipe.ext.uacc.FreeLicenseProvider;
}
