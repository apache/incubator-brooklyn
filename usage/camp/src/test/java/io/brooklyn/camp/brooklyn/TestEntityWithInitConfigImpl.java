package io.brooklyn.camp.brooklyn;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.location.Location;

public class TestEntityWithInitConfigImpl extends AbstractEntity implements TestEntityWithInitConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TestEntityWithInitConfigImpl.class);
    private Entity entityCachedOnInit;
    
    @Override
    public void init() {
        super.init();
        entityCachedOnInit = getConfig(TEST_ENTITY);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        LOG.trace("Starting {}", this);
    }

    @Override
    public void stop() {
        LOG.trace("Stopping {}", this);
    }

    @Override
    public void restart() {
        LOG.trace("Restarting {}", this);
    }

    public Entity getEntityCachedOnInit() {
        return entityCachedOnInit;
    }
}
