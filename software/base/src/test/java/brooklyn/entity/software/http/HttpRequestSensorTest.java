package brooklyn.entity.software.http;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class HttpRequestSensorTest {
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString");
    final static String TARGET_TYPE = "java.lang.String";

    private TestApplication app;
    private EntityLocal entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).location(app.newLocalhostProvisioningLocation().obtain()));
        app.start(ImmutableList.<Location>of());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups="Integration")
    public void testHttpSensor() throws Exception {
        HttpRequestSensor<Integer> sensor = new HttpRequestSensor<Integer>(ConfigBag.newInstance()
                .configure(HttpRequestSensor.SENSOR_NAME, SENSOR_STRING.getName())
                .configure(HttpRequestSensor.SENSOR_TYPE, TARGET_TYPE)
                .configure(HttpRequestSensor.JSON_PATH, "$.myKey")
                .configure(HttpRequestSensor.SENSOR_URI, "http://echo.jsontest.com/myKey/myValue"));
            sensor.apply(entity);
        entity.setAttribute(Attributes.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_STRING, SENSOR_STRING.getName());
        String val = entity.getConfig(HttpRequestSensor.SENSOR_NAME);
        assertEquals(val, "myValue", "val=" + val);
    }
}
