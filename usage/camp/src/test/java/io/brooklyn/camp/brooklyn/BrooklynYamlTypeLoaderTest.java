package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeLoader;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeLoader.Factory;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeLoader.LoaderFromKey;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.policy.Policy;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

public class BrooklynYamlTypeLoaderTest extends AbstractYamlTest {

    @Test
    public void testLoadPolicySpecProgrammatically() {
        Factory loader = new BrooklynYamlTypeLoader.Factory(mgmt(), "test:"+JavaClassNames.niceClassAndMethod());
        LoaderFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName())).prefix("some");
        
        Assert.assertTrue(decoL.getConfigMap().isEmpty());
        Assert.assertEquals(decoL.getTypeName().get(), ServiceRestarter.class.getName());
        Assert.assertEquals(decoL.getType(), ServiceRestarter.class);
        
        Object sl1 = decoL.newInstance();
        Assert.assertTrue(sl1 instanceof ServiceRestarter);
        
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertTrue(sl2 instanceof ServiceRestarter);
    }
    
    @Test
    public void testLoadPolicySpecWithBrooklynConfig() {
        Factory loader = new BrooklynYamlTypeLoader.Factory(mgmt(), "test:"+JavaClassNames.niceClassAndMethod());
        LoaderFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName(),
            "brooklyn.config", MutableMap.of("failOnRecurringFailuresInThisDuration", Duration.seconds(42)))).prefix("some");
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertEquals(sl2.getConfig(ServiceRestarter.FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION).toSeconds(), 42);
    }

    @Test(groups = "WIP")
    public void testLoadPolicySpecWithFlag() {
        Factory loader = new BrooklynYamlTypeLoader.Factory(mgmt(), "test:"+JavaClassNames.niceClassAndMethod());
        LoaderFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName(),
            "failOnRecurringFailuresInThisDuration", Duration.seconds(42))).prefix("some");
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertEquals(sl2.getConfig(ServiceRestarter.FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION).toSeconds(), 42);
    }

}
