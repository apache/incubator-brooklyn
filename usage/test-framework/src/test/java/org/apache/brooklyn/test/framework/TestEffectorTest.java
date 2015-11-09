package org.apache.brooklyn.test.framework;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author m4rkmckenna on 27/10/2015.
 */
public class TestEffectorTest {

    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private String testId;

    @BeforeMethod
    public void setup() {
        testId = UUID.randomUUID().toString();
        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();

        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", testId));

    }


    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }


    @Test
    public void testSimpleEffector() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));


        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "simpleEffector"));

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isTrue();

        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isNull();
    }

    @Test
    public void testComplexffector() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));

        final long expectedLongValue = System.currentTimeMillis();
        final boolean expectedBooleanValue = expectedLongValue % 2 == 0;

        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "complexEffector")
                .configure(TestEffector.EFFECTOR_PARAMS, ImmutableMap.of(
                        "stringValue", testId,
                        "booleanValue", expectedBooleanValue,
                        "longValue", expectedLongValue)));

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_INVOKED)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_INVOKED)).isTrue();

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_STRING)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_STRING)).isEqualTo(testId);

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_BOOLEAN)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_BOOLEAN)).isEqualTo(expectedBooleanValue);

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_LONG)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_LONG)).isEqualTo(expectedLongValue);

        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isNotNull();
        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isInstanceOf(TestEntity.TestPojo.class);

        final TestEntity.TestPojo effectorResult = (TestEntity.TestPojo) testEffector.sensors().get(TestEffector.EFFECTOR_RESULT);
        assertThat(effectorResult.getBooleanValue()).isEqualTo(expectedBooleanValue);
        assertThat(effectorResult.getStringValue()).isEqualTo(testId);
        assertThat(effectorResult.getLongValue()).isEqualTo(expectedLongValue);

    }

}
