package brooklyn.entity.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJava {

    @SetFromFlag("javaSysProps")
    public static final BasicConfigKey<Map<String, String>> JAVA_SYSPROPS = new BasicConfigKey<Map<String, String>>(
            (Class)Map.class, "java.sysprops", "Java command line system properties", new HashMap<String,String>());

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
    public static final BasicConfigKey<List<String>> JAVA_OPTS = new BasicConfigKey<List<String>>(
            (Class)List.class, "java.opts", "Java command line options", new ArrayList<String>());

    /**
     * @deprecated Use JAVA_SYSPROPS instead; was deprecated in 0.4.0
     */
    @Deprecated
    public static final BasicConfigKey<Map<String, String>> JAVA_OPTIONS = JAVA_SYSPROPS;
}