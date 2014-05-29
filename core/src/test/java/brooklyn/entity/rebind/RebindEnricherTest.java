package brooklyn.entity.rebind;

import org.testng.annotations.Test;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.text.StringFunctions;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class RebindEnricherTest extends RebindTestFixtureWithApp {

    public static AttributeSensor<String> METRIC1 = Sensors.newStringSensor("RebindEnricherTest.metric1");
    public static AttributeSensor<String> METRIC2 = Sensors.newStringSensor("RebindEnricherTest.metric2");
    
    private DynamicCluster origCluster;
    private TestEntity origEntity;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        origCluster = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class).configure("memberSpec", EntitySpec.create(TestEntity.class)));
        origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @Test(enabled=false)
    public void testPropagatingEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagating(METRIC1)
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC1, "myval");
    }

    @Test(enabled=false)
    public void testPropagatingAllEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagatingAll()
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC1, "myval");
    }

    @Test(enabled=false)
    public void testPropagatingAsEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(METRIC1, METRIC2))
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC2, "myval");
    }

    @Test(enabled=false)
    public void testCombiningEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .combining(METRIC1, METRIC2)
                .from(origEntity)
                .computing(StringFunctions.joiner(","))
                .publishing(METRIC2)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        newEntity.setAttribute(METRIC2, "myval2");
        EntityTestUtils.assertAttributeEventually(newApp, METRIC2, Predicates.or(Predicates.equalTo("myval,myval2"), Predicates.equalTo("myval2,myval")));
    }

    @Test(enabled=false)
    public void testAggregatingMembersEnricher() throws Exception {
        origCluster.resize(2);
        
        origApp.addEnricher(Enrichers.builder()
                .aggregating(METRIC1)
                .from(origCluster)
                .fromMembers()
                .computing(StringFunctions.joiner(","))
                .publishing(METRIC2)
                .build());
        
        TestApplication newApp = rebind();
        DynamicCluster newCluster = (DynamicCluster) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicCluster.class));

        int i = 1;
        for (Entity member : newCluster.getMembers()) {
            ((EntityInternal)member).setAttribute(METRIC1, "myval"+(i++));
        }
        EntityTestUtils.assertAttributeEventually(newApp, METRIC2, Predicates.or(Predicates.equalTo("myval1,myval2"), Predicates.equalTo("myval2,myval1")));
    }
}
