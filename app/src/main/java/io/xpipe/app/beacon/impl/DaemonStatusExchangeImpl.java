package io.xpipe.app.beacon.impl;

import io.xpipe.app.core.mode.AppOperationMode;
import io.xpipe.beacon.api.DaemonStatusExchange;

import com.sun.net.httpserver.HttpExchange;

public class DaemonStatusExchangeImpl extends DaemonStatusExchange {

    @Override
    public boolean requiresCompletedStartup() {
        return false;
    }

    @Override
    public Object handle(HttpExchange exchange, Request body) {
        String mode;
        if (AppOperationMode.get() == null) {
            mode = "none";
        } else {
            mode = AppOperationMode.get().getId();
        }

        return Response.builder().mode(mode).build();
    }

    @Override
    public boolean requiresEnabledApi() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        // Allow unauthenticated status queries so health checks and monitoring
        // tools can poll the daemon without needing a token.
        return false;
    }
}
