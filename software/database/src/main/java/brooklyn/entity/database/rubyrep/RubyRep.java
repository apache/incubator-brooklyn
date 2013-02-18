package brooklyn.entity.database.rubyrep;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;


@Catalog(name="RubyRep Entity", description="Configures a RubyRep Node to replicate two Brooklyn database entities", iconUrl="classpath:///rubyrep-logo.jpeg")
@ImplementedBy(RubyRepImpl.class)
public interface RubyRep extends Entity, Startable  {
    /**
     * configuration is set when ruby rep is up and running
     */
    static BasicConfigKey<Boolean> RUBYREP_READY = new BasicConfigKey<Boolean>(Boolean.class, "rubyrep.serviceUp", "");
    /**
     * Hostname of the machine running ruby rep
     */
    static BasicConfigKey<String> RUBYREP_HOSTNAME = new BasicConfigKey.StringConfigKey("rubyrep.hostname", "rubyrep hostname", "");   
}
