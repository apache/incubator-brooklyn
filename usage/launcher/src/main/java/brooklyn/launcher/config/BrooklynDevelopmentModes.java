package brooklyn.launcher.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;

public class BrooklynDevelopmentModes {

    private static final Logger log = LoggerFactory.getLogger(BrooklynDevelopmentModes.class);
    
    public static final ConfigKey<BrooklynDevelopmentMode> BROOKLYN_DEV_MODE = new BasicConfigKey<BrooklynDevelopmentMode>(
            BrooklynDevelopmentMode.class, "brooklyn.developmentMode", "whether to run in development mode " +
                    "(default is to autodetect based on classpath)", BrooklynDevelopmentMode.AUTO);

    private static AtomicBoolean loggedMode = new AtomicBoolean(false); 
    
    public static enum BrooklynDevelopmentMode {
        TRUE(true), FALSE(false), AUTO(null);

        private final Boolean enabled;
        
        BrooklynDevelopmentMode(Boolean enabled) {
            this.enabled = enabled;
        }
        
        public boolean isEnabled() {
            boolean enabled = computeEnabled();
            if (!loggedMode.getAndSet(true)) {
                // log on first invocation
                String reason = (this.enabled==null ? "autodetected" : "forced");
                if (enabled) {
                    log.info("Brooklyn running in development mode ("+reason+")");
                } else {
                    log.debug("Brooklyn not running in development mode ("+reason+")");
                }
            }
            return enabled;
        }
        
        protected boolean computeEnabled() {
            if (enabled!=null) return enabled;
            return getAutodectectedDevelopmentMode();
        }
    }
    
    private static Boolean developmentMode = null;
    
    public static boolean getAutodectectedDevelopmentMode() {
        if (developmentMode!=null) return developmentMode;
        developmentMode = computeAutodectectedDevelopmentMode();
        return developmentMode;
    }
    
    private static final String segment = "/core/target/classes";
    
    private static boolean computeAutodectectedDevelopmentMode() {
        String cp = System.getProperty("java.class.path");
        if (cp==null) return false;
        if (cp.endsWith(segment) || cp.contains(segment+":") || cp.contains(segment+";")) {
            log.debug("Brooklyn developmentMode autodetected (based on presence of '"+segment+"' in classpath)");
            return true;
        }
        return false;
    }
    
}
