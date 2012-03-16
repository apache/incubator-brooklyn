package brooklyn.launcher

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.launcher.WebAppRunner
import brooklyn.management.internal.AbstractManagementContext

class BrooklynLauncher {
    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncher.class)
    
    public static void manage(AbstractApplication app, int port=8081) {
        // Locate the management context
        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)

        // Start the web console service
        WebAppRunner web
        try {
            web = new WebAppRunner(context, port)
            web.start()
        } catch (Exception e) {
            LOG.warn("Failed to start Brooklyn web-console", e)
        }

        addShutdownHook {
            LOG.info("Brooklyn launcher's shutdown-hook invoked: shutting down application")
            app?.stop()
        }
        //do these in parallel on shutdown; whilst it would be nice to watch the application closing
        //in the browser, spring is also listening to the shutdown hook so leaving the server up just
        //causes lots of stack trace messages if a browser is trying to hit it!
        addShutdownHook {
            LOG.info("Brooklyn launcher's shutdown-hook invoked: shutting down web-console")
            web?.stop()
        }
    }
}
