package brooklyn.test.entity;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;

@ImplementedBy(TestApplication2Impl.class)
public interface TestApplication2 extends Application, Startable {

    public <T extends Entity> T createChild(EntitySpec<T> spec);

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);
}
