package brooklyn.entity.rebind;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;

public class RebindTestFixtureWithApp extends RebindTestFixture<TestApplication> {

    protected TestApplication createApp() {
        return ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), origManagementContext);
    }
    
}
