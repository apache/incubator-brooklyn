package brooklyn.entity.webapp.jboss;

import groovy.time.TimeDuration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.adapter.ConfigSensorAdapter;
import brooklyn.event.adapter.JmxObjectNameAdapter;
import brooklyn.event.adapter.JmxSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;

public class JBoss6Server extends JavaWebAppSoftwareProcess implements JavaWebAppService, UsesJmx {

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

        sensorRegistry.register(new ConfigSensorAdapter());

        Map<String, Object> flags = new HashMap<String, Object>();
        flags.put("period", new TimeDuration(0, 0, 0, 500));
        JmxSensorAdapter jmx = sensorRegistry.register(new JmxSensorAdapter(flags));
        JmxObjectNameAdapter objectNameAdapter = jmx.objectName("jboss.web:type=GlobalRequestProcessor,name=http-*");
        objectNameAdapter.attribute("errorCount").subscribe(ERROR_COUNT);
        objectNameAdapter.attribute("requestCount").subscribe(REQUEST_COUNT);
        objectNameAdapter.attribute("processingTime").subscribe(TOTAL_PROCESSING_TIME);
        jmx.objectName("jboss.system:type=Server").attribute("Started").subscribe(SERVICE_UP);
        
        // If MBean is unreachable, then mark as service-down
        jmx.objectName("jboss.system:type=Server").reachable().poll(new Function<Boolean,Void>() {
                @Override public Void apply(Boolean input) {
                    if (input != null && Boolean.FALSE.equals(input)) {
                        setAttribute(SERVICE_UP, false);
                    }
                    return null;
                }});
    }

    @Override
    public Class<JBoss6Driver> getDriverInterface() {
        return JBoss6Driver.class;
    }
}
