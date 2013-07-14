package brooklyn.entity.webapp.jetty;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Jetty instance.
 */
@Catalog(name="Jetty6 Server", description="Old version (v6 @ Mortbay) of the popular Jetty webapp container", iconUrl="classpath:///jetty-logo.png")
@ImplementedBy(Jetty6ServerImpl.class)
public interface Jetty6Server extends JettyServer {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "6.1.26");

}
