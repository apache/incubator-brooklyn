package brooklyn.entity.java;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public interface UsesJava {

    /** system properties (-D) to append to JAVA_OPTS; normally accessed through {@link JavaEntityMethods#javaSysProp(String)} */
    @SetFromFlag("javaSysProps")
    public static final MapConfigKey<String> JAVA_SYSPROPS = new MapConfigKey<String>(String.class,
            "java.sysprops", "Java command line system properties", Maps.<String,String>newLinkedHashMap());

    /**
     * Used to set java options. These options are pre-pended to the defaults.
     * They can also be used to override defaults. The rules for overrides are:
     * <ul>
     *   <li>If contains a mutually exclusive setting, then the others are removed. Those supported are:
     *     <ul>
     *       <li>"-client" and "-server"
     *     </ul>
     *   <li>If value has a well-known prefix indicating it's a key-value pair. Those supported are:
     *     <ul>
     *       <li>"-Xmx"
     *       <li>"-Xms"
     *       <li>"-Xss"
     *     </ul>
     *   <li>If value contains "=" then see if there's a default that matches the section up to the "=".
     *       If there is, then remove the original and just include this.
     *       e.g. "-XX:MaxPermSize=512m" could be overridden in this way.
     * </ul> 
     */
    @SetFromFlag("javaOpts")
    public static final SetConfigKey<String> JAVA_OPTS = new SetConfigKey<String>(String.class, 
            "java.opts", "Java command line options", ImmutableSet.<String>of());

    public static final ConfigKey<Boolean> CHECK_JAVA_HOSTNAME_BUG = ConfigKeys.newBooleanConfigKey( 
            "java.check.hostname.bug", "Check whether hostname is too long and will likely crash Java" +
            		"due to bug 7089443", true);

}