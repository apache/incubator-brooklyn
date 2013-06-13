package brooklyn.test.entity;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

/**
 * Mock application for testing.
 */
//TODO Don't want to extend EntityLocal/EntityInternal, but tests want to call things like app.setAttribute
@ImplementedBy(TestApplicationImpl.class)
public interface TestApplication extends StartableApplication, EntityLocal, EntityInternal {

    public static final AttributeSensor<String> MY_ATTRIBUTE = new BasicAttributeSensor<String>(String.class, "test.myattribute", "Test attribute sensor");

    public <T extends Entity> T createChild(EntitySpec<T> spec);

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);

    /**
     * convenience for wiring in management during testing
     * 
     * @deprecated Use Entities.startManagement(app)
     */
    @Deprecated
    public void startManagement();
    
    /**
     * convenience for wiring in management during testing
     * 
     * @deprecated Use Entities.manage(entity)
     */
    @Deprecated
    public <T extends Entity> T manage(T entity);
}
