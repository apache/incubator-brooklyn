package brooklyn.entity.webapp.jboss;

import java.util.Map;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(JBoss6ServerImpl.class)
public interface JBoss6Server extends JavaWebAppSoftwareProcess, JavaWebAppService, UsesJmx {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "6.0.0.Final");
    @SetFromFlag("portIncrement")
    public static final BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "jboss.portincrement", "Increment to be used for all jboss ports", 0);
    @SetFromFlag("clusterName")
    public static final BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss.clusterName", "Identifier used to group JBoss instances", "");

    /**
     * @deprecated will be deleted in 0.5. Unsupported in 0.4.0.
     */
    @Deprecated
    //TODO property copied from legacy JavaApp, but underlying implementation has not been
    public static final MapConfigKey<Map> PROPERTY_FILES =
            new MapConfigKey<Map>(Map.class, "java.properties.environment", "Property files to be generated, referenced by an environment variable");
}
