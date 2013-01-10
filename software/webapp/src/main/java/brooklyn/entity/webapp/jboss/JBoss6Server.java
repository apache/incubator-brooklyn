package brooklyn.entity.webapp.jboss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Functions;

public class JBoss6Server extends JavaWebAppSoftwareProcessImpl implements JavaWebAppService, UsesJmx {

    public static final Logger log = LoggerFactory.getLogger(JBoss6Server.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "6.0.0.Final");
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

    private JmxFeed jmxFeed;
    
    public JBoss6Server(Entity parent) {
        this(new LinkedHashMap(), parent);
    }

    public JBoss6Server(Map flags){
        this(flags, null);
    }

    public JBoss6Server(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        String requestProcessorMbeanName = "jboss.web:type=GlobalRequestProcessor,name=http-*";
        String serverMbeanName = "jboss.system:type=Server";
        
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
                        .objectName(requestProcessorMbeanName)
                        .attributeName("errorCount"))
                .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                        .objectName(requestProcessorMbeanName)
                        .attributeName("requestCount"))
                .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .objectName(requestProcessorMbeanName)
                        .attributeName("processingTime"))
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(serverMbeanName)
                        .attributeName("Started")
                        .onError(Functions.constant(false)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (jmxFeed != null) jmxFeed.stop();
    }
    
    @Override
    public Class<JBoss6Driver> getDriverInterface() {
        return JBoss6Driver.class;
    }
}
