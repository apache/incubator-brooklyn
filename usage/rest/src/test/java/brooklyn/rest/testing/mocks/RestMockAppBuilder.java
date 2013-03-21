package brooklyn.rest.testing.mocks;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpecs;

public class RestMockAppBuilder extends ApplicationBuilder {

    public RestMockAppBuilder() {
        super(EntitySpecs.spec(StartableApplication.class).impl(RestMockApp.class));
    }
    
    @Override
    protected void doBuild() {
        addChild(EntitySpecs.spec(Entity.class).impl(RestMockSimpleEntity.class).displayName("child1"));
    }
}
