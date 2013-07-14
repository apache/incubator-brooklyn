package brooklyn.entity.webapp.jetty;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Jetty instance.
 */
// currently points to v6 impl, but we should probably support a later version, and bump this to point to that when we do
@ImplementedBy(Jetty6ServerImpl.class)
public interface JettyServer extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    class Spec<T extends JettyServer, S extends Spec<T,S>> extends BasicEntitySpec<T,S> {

        private static class ConcreteSpec extends Spec<JettyServer, ConcreteSpec> {
            ConcreteSpec() {
                super(JettyServer.class);
            }
        }

        public static Spec<JettyServer, ?> newInstance() {
            return new ConcreteSpec();
        }

        protected Spec(Class<T> type) {
            super(type);
        }
    }

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "6.1.26");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://dist.codehaus.org/jetty/jetty-${version}/jetty-${version}.zip");

    BasicAttributeSensor<String> JMX_SERVICE_URL = Attributes.JMX_SERVICE_URL;

    public static final AttributeSensor<Integer> RESPONSES_4XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.4xx", "Responses in the 400's");

    public static final AttributeSensor<Integer> RESPONSES_5XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.5xx", "Responses in the 500's");

}
