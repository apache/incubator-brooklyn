package brooklyn.entity.webapp;

import groovy.transform.InheritConstructors

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.entity.basic.BasicConfigurableEntityFactory
import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.webapp.ElasticJavaWebAppService.ElasticJavaWebAppServiceAwareLocation
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class ElasticCustomLocationTest {

    @InheritConstructors
    public static class MockWebServiceLocation extends SimulatedLocation implements ElasticJavaWebAppServiceAwareLocation {
        @Override
        public ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory() {
            return new BasicConfigurableEntityFactory(MockWebService.class);
        }
    }
    
    @InheritConstructors
    public static class MockWebService extends TestEntity implements ElasticJavaWebAppService { 
    }
    
    @Test
    public void testElasticClusterCreatesTestEntity() {
        MockWebServiceLocation l = []
        def app = new TestApplication();
        app.setConfig(MockWebService.ROOT_WAR, "WAR0");
        app.setConfig(MockWebService.NAMED_WARS, ["ignore://WARn"]);
        
        ElasticJavaWebAppService svc = 
            new ElasticJavaWebAppService.Factory().newFactoryForLocation(l).newEntity(war: "WAR1", app);
        Assert.assertTrue(svc in MockWebService, "expected MockWebService, got "+svc);
        //check config has been set correctly, where overridden, and where inherited
        Assert.assertEquals(svc.getConfig(MockWebService.ROOT_WAR), "WAR1");
        Assert.assertEquals(svc.getConfig(MockWebService.NAMED_WARS), ["ignore://WARn"]);
    }
    
}
