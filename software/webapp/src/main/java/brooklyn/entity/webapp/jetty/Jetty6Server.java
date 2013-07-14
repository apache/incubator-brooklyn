package brooklyn.entity.webapp.jetty;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Jetty instance.
 */
@Catalog(name="Jetty6 Server", description="Old version (v6 @ Mortbay) of the popular Jetty webapp container", iconUrl="classpath:///jetty-logo.png")
@ImplementedBy(Jetty6ServerImpl.class)
public interface Jetty6Server extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "6.1.26");

    ConfigKey<Integer> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, (int)Duration.FIVE_MINUTES.toSeconds());
    
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://dist.codehaus.org/jetty/jetty-${version}/jetty-${version}.zip");

    BasicAttributeSensor<String> JMX_SERVICE_URL = Attributes.JMX_SERVICE_URL;

    public static final AttributeSensor<Integer> RESPONSES_4XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.4xx", "Responses in the 400's");

    public static final AttributeSensor<Integer> RESPONSES_5XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.5xx", "Responses in the 500's");

}
