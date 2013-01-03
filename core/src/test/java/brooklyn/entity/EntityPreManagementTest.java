package brooklyn.entity;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;


@SuppressWarnings({"rawtypes","unchecked"})
public class EntityPreManagementTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EntityPreManagementTest.class);
    
    @Test
    public void testSetSensorBeforeManaged() {
        TestEntity e = new TestEntity();
        e.setAttribute(Attributes.HOSTNAME, "martian.martian");
        Assert.assertEquals(e.getAttribute(Attributes.HOSTNAME), "martian.martian");
        
        Assert.assertFalse(e.getManagementSupport().isManagementContextReal());
    }
    
    @Test
    public void testAddPolicyToEntityBeforeManaged() {
        TestEntity e = new TestEntity();
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
        
        TestApplication app = new TestApplication();
        e.setParent(app);
        new LocalManagementContext().manage(app);
//        app.start(Arrays.<Location>asList());
        
        TestUtils.assertEventually(new Runnable() {
            @Override
            public void run() {
                if (events.isEmpty()) Assert.fail("no events received");
            }});
        Assert.assertEquals(events.size(), 1, "Expected 1 event; got: "+events);
    }

    @Test
    public void testAddPolicyToApplicationBeforeManaged() {
        TestApplication app = new TestApplication();
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
//        Assert.assertEquals(app.getManagementContext(), null);
        
        new LocalManagementContext().manage(app);
//        app.start(Arrays.<Location>asList());
        
        TestUtils.assertEventually(new Runnable() {
            @Override
            public void run() {
                if (events.isEmpty()) Assert.fail("no events received");
            }});
        Assert.assertEquals(events.size(), 1, "Expected 1 event; got: "+events);
    }

}
