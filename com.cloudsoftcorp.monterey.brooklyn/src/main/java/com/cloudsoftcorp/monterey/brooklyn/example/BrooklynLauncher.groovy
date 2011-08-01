package com.cloudsoftcorp.monterey.brooklyn.example

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.launcher.WebAppRunner
import brooklyn.management.internal.AbstractManagementContext

class BrooklynLauncher {
    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncher.class)
    
    public static void manage(AbstractApplication app) {
        // Locate the management context
        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)

        // Start the web console service
        WebAppRunner web
        try {
            web = new WebAppRunner(context)
            web.start()
        } catch (Exception e) {
            LOG.warn("Failed to start web-console", e)
        }

        addShutdownHook {
            app?.stop()
            web?.stop()
        }
    }
}
