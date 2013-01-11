package brooklyn.test.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Mock application for testing.
 */
public class TestApplication2Impl extends AbstractApplication implements TestApplication2 {
	protected static final Logger LOG = LoggerFactory.getLogger(TestApplication2Impl.class);

    public TestApplication2Impl() {
        super();
    }

    @Override
    public <T extends Entity> T createChild(EntitySpec<T> spec) {
        T child = getManagementSupport().getManagementContext(false).getEntityManager().createEntity(spec);
        addChild(child);
        return child;
    }

    @Override
    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec) {
        if (!getManagementSupport().isDeployed()) throw new IllegalStateException("Entity "+this+" not managed");
        T child = createChild(spec);
        getManagementSupport().getManagementContext(false).getEntityManager().manage(child);
        return child;
    }
}
