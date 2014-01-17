package brooklyn.entity.basic;

import static org.testng.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public class DataEntityTest {

    private ManagementContext managementContext;
    private SimulatedLocation loc;
    private TestApplication app;
    private DataEntity entity;
    private AttributeSensor<String>  stringSensor = Sensors.newStringSensor("string", "String sensor");
    private AttributeSensor<Long>  longSensor = Sensors.newLongSensor("long", "Long sensor");
    private AtomicReference<String> reference = new AtomicReference<String>();
    private Supplier<Long> currentTimeMillis = new Supplier<Long>() {
        @Override
        public Long get() {
            return System.currentTimeMillis();
        }
    };
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAllCatching(managementContext);
    }

    @Test
    public void testSupplierSetsAttribute() throws Exception {
        entity = app.addChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 100l)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>of(stringSensor, new Supplier<String>() {
                    @Override
                    public String get() {
                        return reference.get();
                    }
                })));
        Entities.startManagement(entity);
        app.start(ImmutableList.of(loc));

        reference.set("new");
        Thread.sleep(200l); // Twice polling period
        String value = entity.getAttribute(stringSensor);

        assertNotNull(value);
        assertEquals(value, "new");
    }

    @Test
    public void testSupplierIsPolled() throws Exception {
        entity = app.addChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 100l)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>of(longSensor, currentTimeMillis)));
        Entities.startManagement(entity);
        app.start(ImmutableList.of(loc));

        Thread.sleep(200l);
        Long first = entity.getAttribute(longSensor);
        Thread.sleep(200l);
        Long second = entity.getAttribute(longSensor);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(second.longValue() > first.longValue());
    }

    @Test
    public void testWithMultipleSuppliers() throws Exception {
        entity = app.addChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 100l)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>builder()
                        .put(longSensor, currentTimeMillis)
                        .put(stringSensor, new Supplier<String>() {
                                @Override
                                public String get() {
                                    return reference.get();
                                }
                            })
                        .build()));
        Entities.startManagement(entity);
        app.start(ImmutableList.of(loc));

        reference.set("value");
        Thread.sleep(200l);
        Long first = entity.getAttribute(longSensor);
        String second = entity.getAttribute(stringSensor);

        assertNotNull(first);
        assertNotNull(second);
    }
}
