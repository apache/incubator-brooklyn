package brooklyn.extras.openshift;

import static brooklyn.util.JavaGroovyEquivalents.elvis;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class OpenshiftExpressApplicationAccess extends OpenshiftExpressAccess {
    
    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftExpressApplicationAccess.class);

    @SetFromFlag
    Cartridge cartridge;
    
    @SetFromFlag(nullable=false)
    String appName;
    
    @SetFromFlag(defaultVal="false")
    boolean debug;

    OpenshiftExpressApplicationAccess(Map flags) {
        super(flags);
        if (cartridge == null) cartridge = Cartridge.JBOSS_AS_7; // TODO better way for default vals that aren't string?

    }
    
    @Override
    protected void validate() {
        super.validate();
        if (!(appName.matches("[A-Za-z0-9]+"))) throw new IllegalArgumentException("appName must contain only alphanumeric characters");
    }

    // FIXME What was newFields doing in groovy? Anything special?
    Map newFields(Map f) {
        Map f2 = Maps.newLinkedHashMap();
        f2.putAll(ImmutableMap.of("cartridge", cartridge, "app_name", appName, "debug", ""+debug));
        f2.putAll(f);
        return f2;
    }
    
    /** returns info on an app; note, throws IllegalState on http error response 500 if doesn't exist */
    public Object status() {
        return doPost(newFields(MutableMap.of("action", "status") ));
    }
    
    /** creates (and starts) an app; accepts 'retries' flags which is useful because openshift sometimes returns 500 server error */
    public Object create() {
        return create(MutableMap.of());
    }
    
    public Object create(Map flags) {
        int retries = (Integer) elvis(flags.get("retries"), 0);

        boolean retry=true;
        while (retry) {
            retry = false;
            try {
                return doPost(newFields(ImmutableMap.of("action", "configure") ));
            } catch (IllegalStateException e) {
                if (e.toString().indexOf("HTTP/1.1 500 Internal Server Error") >= 0 && retries > 0) {
                    retries-=1;
                    retry=true;
                } else {
                    if (e.toString().indexOf("HTTP/1.1 400") >= 0)
                        LOG.warn("creation of application failed (exception will be thrown); note that this can be due to already having the maximum number of applications in Openshift (currently 4)");
                    throw e;
                }
            }
        }
        throw new IllegalStateException("unreachable code");
    }
    
    /** deletes (and stops) an app */
    public Object destroy() {
        return doPost(newFields(ImmutableMap.of("action", "deconfigure") ));
    }
}
