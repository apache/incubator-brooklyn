package brooklyn.util.logging;

import org.slf4j.bridge.SLF4JBridgeHandler;

public class LoggingSetup {

    /** bridge java.util.logging messages to slf4j
     * <p>
     * without this, we get ugly java.util.logging messages on the console and _not_ in the file;
     * with this, the excludes rules (which route the common j.u.l categories to the file _only_) 
     * will apply to j.u.l loggers 
     * <p>
     * typically this is invoked in a static block on a class (in tests and in BrooklynWebServer) 
     * or could be done on app startup */
    public static void installJavaUtilLoggingBridge() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
    

}
