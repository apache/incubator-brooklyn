package brooklyn.rest.testing.mocks;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;

public class RestMockAppBuilder extends ApplicationBuilder {

    public RestMockAppBuilder() {
        super(EntitySpec.create(StartableApplication.class).impl(RestMockApp.class));
    }
    
    @Override
    protected void doBuild() {
        addChild(EntitySpec.create(Entity.class).impl(RestMockSimpleEntity.class).displayName("child1"));
    }
}
