package brooklyn.launcher;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.management.internal.AbstractManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrooklynLauncher {

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncher.class);

    public static void manage(AbstractApplication app) {
        manage(app, 8081, true, true);
    }

    public static void manage(final AbstractApplication app, int port){
        manage(app, port,true,true);
    }

    public static void manage(final AbstractApplication app, int port, boolean shutdownApp, boolean startWebConsole) {
        // Locate the management context
        AbstractManagementContext context = app.getManagementContext();
        context.manage(app);

        // Start the web console service
        if (startWebConsole) {
            try {
                final WebAppRunner web = new WebAppRunner(context, port);
                web.start();
                //do these in parallel on shutdown; whilst it would be nice to watch the application closing
                //in the browser, spring is also listening to the shutdown hook so leaving the server up just
                //causes lots of stack trace messages if a browser is trying to hit it!

                addShutdownHook(new Runnable() {
                    @Override
                    public void run() {
                        LOG.info("Brooklyn launcher's shutdown-hook invoked: shutting down web-console");
                        try {
                            web.stop();
                        } catch (Exception e) {
                            LOG.error("Failed to execute web.stop", e);
                        }
                    }
                });
            } catch (Exception e) {
                LOG.warn("Failed to start Brooklyn web-console", e);
            }
        }

        if (shutdownApp) {
            addShutdownHook(new Runnable() {
                public void run() {
                    LOG.info("Brooklyn launcher's shutdown-hook invoked: shutting down application");
                    app.stop();
                }
            });
        }
    }

    private static void addShutdownHook(final Runnable task) {
        Runtime.getRuntime().addShutdownHook(new Thread("shutdownHookThread") {
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.error("Failed to execute shutdownhook", e);
                }
            }
        });
    }
}
