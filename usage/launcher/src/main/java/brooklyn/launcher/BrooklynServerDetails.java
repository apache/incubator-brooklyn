package brooklyn.launcher;

import brooklyn.management.ManagementContext;

public class BrooklynServerDetails {

    protected BrooklynWebServer webServer;
    protected ManagementContext mgmtContext;
    
    public BrooklynServerDetails(BrooklynWebServer webServer, ManagementContext mgmtContext) {
        super();
        this.webServer = webServer;
        this.mgmtContext = mgmtContext;
    }

    public BrooklynWebServer getWebServer() {
        return webServer;
    }
    
    public String getWebServerUrl() {
        if (webServer==null) return null;
        return webServer.getRootUrl();
    }
    
    public ManagementContext getManagementContext() {
        return mgmtContext;
    }
    
}
