package brooklyn.entity.webapp.tomcat;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
@Catalog(name="Tomcat Server", description="Apache Tomcat is an open source software implementation of the Java Servlet and JavaServer Pages technologies", iconUrl="classpath:///tomcat-logo.png")
@ImplementedBy(TomcatServerImpl.class)
public interface TomcatServer extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    /** @deprecated since v0.6.0 as does nothing beyond BasicEntitySpec */
    @Deprecated
    class Spec<T extends TomcatServer, S extends Spec<T,S>> extends BasicEntitySpec<T,S> {

        private static class ConcreteSpec extends Spec<TomcatServer, ConcreteSpec> {
            ConcreteSpec() {
                super(TomcatServer.class);
            }
        }

        public static Spec<TomcatServer, ?> newInstance() {
            return new ConcreteSpec();
        }

        protected Spec(Class<T> type) {
            super(type);
        }
    }

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "7.0.39");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz");

    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down.
     * Don't see an easy way to disable it; causes collisions in its out-of-the-box location of 8005,
     * so override default here to a high-numbered port.
     */
    @SetFromFlag("shutdownPort")
    PortAttributeSensorAndConfigKey SHUTDOWN_PORT =
            new PortAttributeSensorAndConfigKey("tomcat.shutdownport", "Suggested shutdown port", PortRanges.fromString("31880+"));

    ConfigKey<Integer> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, (int)Duration.FIVE_MINUTES.toSeconds());
    
    BasicAttributeSensor<String> CONNECTOR_STATUS =
            new BasicAttributeSensor<String>(String.class, "webapp.tomcat.connectorStatus", "Catalina connector state name");

    BasicAttributeSensor<String> JMX_SERVICE_URL = Attributes.JMX_SERVICE_URL;

}
