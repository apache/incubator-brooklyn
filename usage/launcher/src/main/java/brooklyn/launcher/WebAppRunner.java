package brooklyn.launcher;

import java.util.Map;

import brooklyn.management.ManagementContext;

/** @deprecated since 0.6.0 use BrooklynWebServer */
public class WebAppRunner extends BrooklynWebServer {

    public WebAppRunner(ManagementContext managementContext, int port, String warUrl) {
        super(managementContext, port, warUrl);
    }

    public WebAppRunner(ManagementContext managementContext, int port) {
        super(managementContext, port);
    }

    public WebAppRunner(ManagementContext managementContext) {
        super(managementContext);
    }

    public WebAppRunner(Map flags, ManagementContext managementContext) {
        super(flags, managementContext);
    }

}
