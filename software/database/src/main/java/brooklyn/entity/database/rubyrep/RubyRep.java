package brooklyn.entity.database.rubyrep;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.java.UsesJava;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;

public class RubyRep extends AbstractEntity implements Startable, UsesJava {
    public static final Logger log = LoggerFactory.getLogger(RubyRep.class);

    /**
     * configuration is set when ruby rep is up and running
     */
    public static BasicConfigKey<Boolean> RUBYREP_READY = new BasicConfigKey<Boolean>(Boolean.class, "rubyrep.serviceUp", "");
    /**
     * Hostname of the machine running ruby rep
     */
    public static BasicConfigKey<String> RUBYREP_HOSTNAME = new BasicConfigKey.StringConfigKey("rubyrep.hostname", "rubyrep hostname", "");

    RubyRepNode rubyRepNode;

    public RubyRep(Map flags, Entity owner) {
        super(flags, owner);
        DatabaseNode left = (DatabaseNode) flags.remove("left");
        DatabaseNode right = (DatabaseNode) flags.remove("right");

        rubyRepNode = new RubyRepNodeImpl(flags, this);
        rubyRepNode.setConfig(RubyRepNode.LEFT_DATABASE_URL, DependentConfiguration.attributeWhenReady(left, DatabaseNode.DB_URL));
        rubyRepNode.setConfig(RubyRepNode.RIGHT_DATABASE_URL, DependentConfiguration.attributeWhenReady(right, DatabaseNode.DB_URL));

        setConfig(RUBYREP_HOSTNAME, DependentConfiguration.attributeWhenReady(rubyRepNode, RubyRepNode.HOSTNAME));
        setConfig(RUBYREP_READY, DependentConfiguration.attributeWhenReady(rubyRepNode, RubyRepNode.SERVICE_UP));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        StartableMethods.start(this, locations);
    }

    @Override
    public void stop() {
        StartableMethods.stop(this);
        if (log.isDebugEnabled()) log.debug("stopped entity " + this);
    }

    @Override
    public void restart() {
        StartableMethods.restart(this);
        if (log.isDebugEnabled()) log.debug("restarted entity " + this);
    }
}
