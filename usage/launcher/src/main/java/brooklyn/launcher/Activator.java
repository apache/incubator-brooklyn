package brooklyn.launcher;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    public static final Logger log = LoggerFactory.getLogger(Activator.class);

    public void start(BundleContext context) throws Exception {
        //does nothing on startup, just makes resources available
        //(maybe it wants to register a service that others could use?)
        log.info("Starting brooklyn-launcher OSGi bundle");
    }

    public void stop(BundleContext context) throws Exception {
        log.info("Stopping brooklyn-launcher OSGi bundle");
    }
}