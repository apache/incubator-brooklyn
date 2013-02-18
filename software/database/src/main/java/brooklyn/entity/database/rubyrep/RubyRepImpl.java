package brooklyn.entity.database.rubyrep;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.java.UsesJava;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class RubyRepImpl extends AbstractEntity implements RubyRep, UsesJava {
    public static final Logger log = LoggerFactory.getLogger(RubyRepImpl.class);
    RubyRepNode rubyRepNode;

    @Override
    public RubyRepImpl configure(Map flags) {
        DatabaseNode left = (DatabaseNode) flags.remove("left");
        DatabaseNode right = (DatabaseNode) flags.remove("right");

        rubyRepNode = getEntityManager().createEntity(flags, RubyRepNode.class);
        rubyRepNode.setConfig(RubyRepNode.LEFT_DATABASE_URL, DependentConfiguration.attributeWhenReady(left, DatabaseNode.DB_URL));
        rubyRepNode.setConfig(RubyRepNode.RIGHT_DATABASE_URL, DependentConfiguration.attributeWhenReady(right, DatabaseNode.DB_URL));
        addChild(rubyRepNode);

        setConfig(RUBYREP_HOSTNAME, DependentConfiguration.attributeWhenReady(rubyRepNode, RubyRepNode.HOSTNAME));
        setConfig(RUBYREP_READY, DependentConfiguration.attributeWhenReady(rubyRepNode, RubyRepNode.SERVICE_UP));
        return this;
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
