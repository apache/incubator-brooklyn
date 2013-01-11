package brooklyn.test.entity;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;

// TODO Don't want to extend EntityLocal, but tests want to call app.setAttribute
@ImplementedBy(TestApplication2Impl.class)
public interface TestApplication2 extends Application, Startable, EntityLocal {

    public <T extends Entity> T createChild(EntitySpec<T> spec);

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);
}
