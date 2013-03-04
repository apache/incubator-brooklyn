package brooklyn.entity.webapp.jboss;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="JBoss Application Server 6", description="AS6: an open source Java application server from JBoss", iconUrl="classpath:///jboss-logo.png")
@ImplementedBy(JBoss6ServerImpl.class)
public interface JBoss6Server extends JavaWebAppSoftwareProcess, JavaWebAppService, UsesJmx {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "6.0.0.Final");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?" +
            "r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent");

    @SetFromFlag("bindAddress")
    BasicAttributeSensorAndConfigKey<String> BIND_ADDRESS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss6.bind.address", 
                "Address of interface JBoss should listen on, defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", 
                "0.0.0.0");

    @SetFromFlag("portIncrement")
    BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "jboss6.portincrement", "Increment to be used for all jboss ports", 0);

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss6.clusterName", "Identifier used to group JBoss instances", "");

    /** @deprecated will be deleted in 0.5. Unsupported in 0.4.0. */
    @Deprecated
    //TODO property copied from legacy JavaApp, but underlying implementation has not been
    MapConfigKey<Map> PROPERTY_FILES =
            new MapConfigKey<Map>(Map.class, "java.properties.environment", "Property files to be generated, referenced by an environment variable");

}
