package brooklyn.entity;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;


@SuppressWarnings({"rawtypes","unchecked"})
public class EntityPreManagementTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EntityPreManagementTest.class);

    private ManagementContext managementContext;
    private EntityManager entityManager;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        entityManager = managementContext.getEntityManager();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testSetSensorBeforeManaged() {
        TestEntity e = entityManager.createEntity(EntitySpec.create(TestEntity.class));

        e.setAttribute(Attributes.HOSTNAME, "martian.martian");
        Assert.assertEquals(e.getAttribute(Attributes.HOSTNAME), "martian.martian");
        
        Assert.assertFalse(e.getManagementSupport().isManagementContextReal());
    }
    
    @Test
    public void testAddPolicyToEntityBeforeManaged() {
        TestEntity e = entityManager.createEntity(EntitySpec.create(TestEntity.class));
        final List events = new ArrayList();
        
        e.addPolicy(new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                subscribe(entity, Attributes.HOSTNAME, new SensorEventListener() {
                    @Override
                    public void onEvent(SensorEvent event) {
                        events.add(event);
                    }
                });
            }
        });
        
        e.setAttribute(Attributes.HOSTNAME, "martian.martian");
        Assert.assertEquals(e.getAttribute(Attributes.HOSTNAME), "martian.martian");
        
        if (!events.isEmpty()) Assert.fail("Shouldn't have events yet: "+events);
        Assert.assertFalse(e.getManagementSupport().isManagementContextReal());
        
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e.setParent(app);
        Entities.manage(e);
        
        TestUtils.assertEventually(new Runnable() {
            @Override
            public void run() {
                if (events.isEmpty()) Assert.fail("no events received");
            }});
        Assert.assertEquals(events.size(), 1, "Expected 1 event; got: "+events);
    }

    @Test
    public void testAddPolicyToApplicationBeforeManaged() {
        app = entityManager.createEntity(EntitySpec.create(TestApplication.class));
        final List events = new ArrayList();
        
        app.addPolicy(new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                subscribe(entity, Attributes.HOSTNAME, new SensorEventListener() {
                    @Override
                    public void onEvent(SensorEvent event) {
                        events.add(event);
                    }
                });
            }
        });
        
        app.setAttribute(Attributes.HOSTNAME, "martian.martian");
        Assert.assertEquals(app.getAttribute(Attributes.HOSTNAME), "martian.martian");
        
        if (!events.isEmpty()) Assert.fail("Shouldn't have events yet: "+events);
        
        Entities.startManagement(app, managementContext);
        
        TestUtils.assertEventually(new Runnable() {
            @Override
            public void run() {
                if (events.isEmpty()) Assert.fail("no events received");
            }});
        Assert.assertEquals(events.size(), 1, "Expected 1 event; got: "+events);
    }

}
