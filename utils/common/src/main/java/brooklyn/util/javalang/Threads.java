package brooklyn.util.javalang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Threads {

    private static final Logger log = LoggerFactory.getLogger(Threads.class);
    
    public static Thread addShutdownHook(final Runnable task) {
        Thread t = new Thread("shutdownHookThread") {
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Failed to execute shutdownhook", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(t);
        return t;
    }
    
    public static boolean removeShutdownHook(Thread hook) {
        try {
            return Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // probably shutdown in progress
            log.debug("cannot remove shutdown hook "+hook+": "+e);
            return false;
        }
    }

}
