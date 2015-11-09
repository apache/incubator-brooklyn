package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Abstract base class for tests, providing common target lookup.
 */
public abstract class AbstractTest extends AbstractEntity implements BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTest.class);

    /**
     * Find the target entity using "target" config key, if entity provided directly in config, or by doing an implicit
     * lookup using DSL ($brooklyn:component("myNginX")), if id of entity provided as "targetId" config key.
     *
     * @return The target entity.
     *
     * @throws @RuntimeException if no target can be determined.
     */
    public Entity resolveTarget() {
        Entity entity = getConfig(TARGET_ENTITY);
        if (null == entity) {
            entity = getTargetById();
        }
        return entity;
    }

    private Entity getTargetById() {
        String targetId = getConfig(TARGET_ID);
        final Task<Entity> targetLookup = new DslComponent(targetId).newTask();
        Entity entity = null;
        try {
            entity = Tasks.resolveValue(targetLookup, Entity.class, getExecutionContext(), "Finding entity " + targetId);
            LOG.debug("Found target by id {}", targetId);
        } catch (final ExecutionException | InterruptedException e) {
            LOG.error("Error finding target {}", targetId);
            Exceptions.propagate(e);
        }
        return entity;
    }
}
