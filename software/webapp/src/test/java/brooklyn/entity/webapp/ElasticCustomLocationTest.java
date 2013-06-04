package brooklyn.entity.webapp;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.webapp.ElasticJavaWebAppService.ElasticJavaWebAppServiceAwareLocation;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class ElasticCustomLocationTest {

    public static class MockWebServiceLocation extends SimulatedLocation implements ElasticJavaWebAppServiceAwareLocation {
        public MockWebServiceLocation() {
        }
        
        @Override
        public ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory() {
            return new BasicConfigurableEntityFactory(MockWebService.class);
        }
    }
    
    public static class MockWebService extends TestEntityImpl implements ElasticJavaWebAppService {
        public MockWebService() {
        } 
        // TODO Used by asicConfigurableEntityFactory.newEntity2, via MockWebServiceLocation.newWebClusterFactory
        public MockWebService(Map flags, Entity parent) {
            super(flags, parent);
        } 
    }
    
    @Test
    public void testElasticClusterCreatesTestEntity() {
        MockWebServiceLocation l = new MockWebServiceLocation();
        TestApplication app = new TestApplicationImpl();
        app.setConfig(MockWebService.ROOT_WAR, "WAR0");
        app.setConfig(MockWebService.NAMED_WARS, ImmutableList.of("ignore://WARn"));
        
        ElasticJavaWebAppService svc = 
            new ElasticJavaWebAppService.Factory().newFactoryForLocation(l).newEntity(MutableMap.of("war", "WAR1"), app);
        Assert.assertTrue(svc instanceof MockWebService, "expected MockWebService, got "+svc);
        //check config has been set correctly, where overridden, and where inherited
        Assert.assertEquals(svc.getConfig(MockWebService.ROOT_WAR), "WAR1");
        Assert.assertEquals(svc.getConfig(MockWebService.NAMED_WARS), ImmutableList.of("ignore://WARn"));
    }
    
}
