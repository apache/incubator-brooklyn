package brooklyn.entity.basic;

import java.util.Arrays;

import org.testng.annotations.Test;

import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

public class EffectorBasicTest {

    // NB: more test of effector in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others
    
    @Test
    public void testInvokeEffectorStart() {
        TestApplication app = new TestApplication();
        new TestEntity(app);
        app.startManagement();
        app.start(Arrays.asList(new SimulatedLocation()));
    }

    @Test
    public void testInvokeEffectorStartWithDependentConfig() {
        TestApplication app = new TestApplication();
        TestEntity entity = new TestEntity(app);
        TestEntity entity2 = new TestEntity(app);
        entity.setConfig(TestEntity.CONF_NAME, DependentConfiguration.attributeWhenReady(entity2, TestEntity.NAME));
        app.startManagement();
        app.start(Arrays.asList(new SimulatedLocation()));
    }
    

}
