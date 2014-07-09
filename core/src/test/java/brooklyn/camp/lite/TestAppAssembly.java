package brooklyn.camp.lite;

import io.brooklyn.camp.spi.Assembly;
import brooklyn.test.entity.TestApplication;

public class TestAppAssembly extends Assembly {

    private TestApplication brooklynApp;

    public TestAppAssembly(TestApplication brooklynApp) {
        this.brooklynApp = brooklynApp;
    }
    
    public TestApplication getBrooklynApp() {
        return brooklynApp;
    }
    
}
