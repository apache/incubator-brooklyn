package brooklyn.rest.testing.mocks;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.BasicEntitySpec;

public class RestMockAppBuilder extends ApplicationBuilder {

    public RestMockAppBuilder() {
        super(BasicEntitySpec.newInstance(StartableApplication.class).impl(RestMockApp.class));
    }
    
    @Override
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(Entity.class).impl(RestMockSimpleEntity.class).displayName("child1"));
    }
}
