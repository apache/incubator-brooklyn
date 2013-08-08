package brooklyn.entity.hello;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.transform;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import groovy.lang.Closure;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.EntityManager;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** tests effector invocation and a variety of sensor accessors and subscribers */
public class LocalEntitiesTest {
	
	public static final Logger log = LoggerFactory.getLogger(LocalEntitiesTest.class);
	
    private SimulatedLocation loc;
    private TestApplication app;
    private EntityManager entityManager;
			
	@BeforeMethod(alwaysRun=true)
	public void setUp() throws Exception {
	    loc = new SimulatedLocation();
	    app = ApplicationBuilder.newManagedApp(TestApplication.class);
	    entityManager = app.getManagementContext().getEntityManager();
	}
	
    @Test
    public void testEffectorUpdatesAttributeSensor() {
        HelloEntity h = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        app.start(ImmutableList.of(loc));
        
        h.setAge(5);
        assertEquals((Integer)5, h.getAttribute(HelloEntity.AGE));
    }

    //REVIEW 1459 - new test
    //subscriptions get notified in separate thread
    @Test
    public void testEffectorEmitsAttributeSensor() throws Exception {
        final HelloEntity h = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        app.start(ImmutableList.of(loc));
        
        final AtomicReference<SensorEvent<?>> evt = new AtomicReference<SensorEvent<?>>();
        final CountDownLatch latch = new CountDownLatch(1);
        
        app.getSubscriptionContext().subscribe(h, HelloEntity.AGE, new SensorEventListener<Integer>() {
            @Override public void onEvent(SensorEvent<Integer> event) {
                evt.set(event);
                latch.countDown();
            }});
        
        long startTime = System.currentTimeMillis();
        
        h.invoke(HelloEntity.SET_AGE, ImmutableMap.of("age", 5));
        assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));

        // observed intermittent failure 2 May 2012. and 14 Jun "after 2 ms". spurious wakeups.
        // code added above to guard against this. (if problem does not recur, remove these comments!)
        assertNotNull(evt.get(), "null response after "+(System.currentTimeMillis()-startTime)+" ms");
        assertEquals(HelloEntity.AGE, evt.get().getSensor());
        assertEquals(h, evt.get().getSource());
        assertEquals(5, evt.get().getValue());
        assertTrue(System.currentTimeMillis() - startTime < 5000);  //shouldn't have blocked for all 5s
    }
    
    //REVIEW 1459 - new test
    @Test
    public void testEffectorEmitsTransientSensor() throws Exception {
        HelloEntity h = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        app.start(ImmutableList.of(loc));
        
        final AtomicReference<SensorEvent<?>> evt = new AtomicReference<SensorEvent<?>>();
        app.getSubscriptionContext().subscribe(h, HelloEntity.ITS_MY_BIRTHDAY, new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                evt.set(event);
                synchronized (evt) {
                    evt.notifyAll();
                }
            }});
        
        long startTime = System.currentTimeMillis();
        synchronized (evt) {
            h.setAge(5);
            evt.wait(5000);
        }
        assertNotNull(evt.get());
        assertEquals(HelloEntity.ITS_MY_BIRTHDAY, evt.get().getSensor());
        assertEquals(h, evt.get().getSource());
        assertNull(evt.get().getValue());
        assertTrue(System.currentTimeMillis() - startTime < 5000);  //shouldn't have blocked for all 5s
    }

    @Test
    public void testSendMultipleInOrderThenUnsubscribe() throws Exception {
        HelloEntity h = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        app.start(ImmutableList.of(loc));

        final List<Integer> data = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(5);
        
        app.getSubscriptionContext().subscribe(h, HelloEntity.AGE, new SensorEventListener<Integer>() {
            @Override public void onEvent(SensorEvent<Integer> event) {
                data.add(event.getValue());
                Time.sleep((int)(20*Math.random()));
                log.info("Thread "+Thread.currentThread()+" notify on subscription received for "+event.getValue()+", data is "+data);
                latch.countDown();
            }});
        
        Stopwatch stopwatch = new Stopwatch().start();
        for (int i = 1; i <= 5; i++) {
            h.setAge(i);
        }
        assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));

        app.getSubscriptionContext().unsubscribeAll();
        h.setAge(6);
        long totalTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        // TODO guava util for (1..5)
        Asserts.continually(MutableMap.of("timeout", 50), Suppliers.ofInstance(data), Predicates.<Object>equalTo(ImmutableList.of(1,2,3,4,5)));
        assertTrue(totalTime < 2000, "totalTime="+totalTime);  //shouldn't have blocked for anywhere close to 2s (Aled says TODO: too time sensitive for BuildHive?)
    }

    @Test
    public void testConfigSetFromAttribute() {
        app.setConfig(HelloEntity.MY_NAME, "Bob");
        
        HelloEntity dad = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        HelloEntity son = entityManager.createEntity(EntitySpec.create(HelloEntity.class).parent(dad));
        Entities.manage(son);
        
        //config is inherited
        assertEquals("Bob", app.getConfig(HelloEntity.MY_NAME));
        assertEquals("Bob", dad.getConfig(HelloEntity.MY_NAME));
        assertEquals("Bob", son.getConfig(HelloEntity.MY_NAME));
        
        //attributes are not
        app.setAttribute(HelloEntity.FAVOURITE_NAME, "Carl");
        assertEquals("Carl", app.getAttribute(HelloEntity.FAVOURITE_NAME));
        assertEquals(null, dad.getAttribute(HelloEntity.FAVOURITE_NAME));
    }
	@Test
	public void testConfigSetFromAttributeWhenReady() throws Exception {
		app.setConfig(HelloEntity.MY_NAME, "Bob");
		
        final HelloEntity dad = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        final HelloEntity son = entityManager.createEntity(EntitySpec.create(HelloEntity.class)
                .parent(dad)
                .configure(HelloEntity.MY_NAME, attributeWhenReady(dad, HelloEntity.FAVOURITE_NAME
                        /* third param is closure; defaults to groovy truth (see google), but could be e.g.
                           , { it!=null && it.length()>0 && it!="Jebediah" }
                         */ )));
        Entities.manage(son);
        
		app.start(ImmutableList.of(loc));
		 
        final Semaphore s1 = new Semaphore(0);
        final Object[] sonsConfig = new Object[1];
        Thread t = new Thread(new Runnable() {
            public void run() {
    			log.info("started");
    			s1.release();
            	log.info("getting config "+sonsConfig[0]);
            	sonsConfig[0] = son.getConfig(HelloEntity.MY_NAME);
            	log.info("got config {}", sonsConfig[0]);
                s1.release();
            }});
                
		log.info("starting");
        long startTime = System.currentTimeMillis();
		t.start();
		log.info("waiting {}", System.identityHashCode(sonsConfig));
        if (!s1.tryAcquire(2, TimeUnit.SECONDS)) fail("race mismatch, missing permits");
        
        //thread should be blocking on call to getConfig
        assertTrue(t.isAlive());
		assertTrue(System.currentTimeMillis() - startTime < 1500);
        synchronized (sonsConfig) {
            assertEquals(null, sonsConfig[0]);
            for (Task tt : ((EntityInternal)dad).getExecutionContext().getTasks()) { log.info("task at dad:  {}, {}", tt, tt.getStatusDetail(false)); }
            for (Task tt : ((EntityInternal)son).getExecutionContext().getTasks()) { log.info("task at son:  {}, {}", tt, tt.getStatusDetail(false)); }
            ((EntityLocal)dad).setAttribute(HelloEntity.FAVOURITE_NAME, "Dan");
            if (!s1.tryAcquire(2, TimeUnit.SECONDS)) fail("race mismatch, missing permits");
        }
		log.info("dad: "+dad.getAttribute(HelloEntity.FAVOURITE_NAME));
		log.info("son: "+son.getConfig(HelloEntity.MY_NAME));
		
        //shouldn't have blocked for very long at all
        assertTrue(System.currentTimeMillis() - startTime < 1500);
        //and sons config should now pick up the dad's attribute
        assertEquals(sonsConfig[0], "Dan");
	}
	
	@Test
	public void testConfigSetFromAttributeWhenReadyTransformations() {
		app.setConfig(HelloEntity.MY_NAME, "Bob");
		
        HelloEntity dad = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        HelloEntity son = entityManager.createEntity(EntitySpec.create(HelloEntity.class)
                .parent(dad)
                .configure(HelloEntity.MY_NAME, transform(attributeWhenReady(dad, HelloEntity.FAVOURITE_NAME), new Function<String,String>() {
                    public String apply(String input) {
                        return input+input.charAt(input.length()-1)+"y";
                    }})));
        Entities.manage(son);
        
		app.start(ImmutableList.of(loc));
		((EntityLocal)dad).setAttribute(HelloEntity.FAVOURITE_NAME, "Dan");
        assertEquals(son.getConfig(HelloEntity.MY_NAME), "Danny");
    }
	
    @Test
    public void testConfigSetFromAttributeWhenReadyNullTransformations() {
        app.setConfig(HelloEntity.MY_NAME, "Bob");
        
        HelloEntity dad = app.createAndManageChild(EntitySpec.create(HelloEntity.class));
        HelloEntity son = entityManager.createEntity(EntitySpec.create(HelloEntity.class)
                .parent(dad)
                .configure(HelloEntity.MY_NAME, transform(attributeWhenReady(dad, HelloEntity.FAVOURITE_NAME, (Closure)null), new Function<String,String>() {
                    public String apply(String input) {
                        return input+input.charAt(input.length()-1)+"y";
                    }})));
        Entities.manage(son);
        
        app.start(ImmutableList.of(loc));
        ((EntityLocal)dad).setAttribute(HelloEntity.FAVOURITE_NAME, "Dan");
        assertEquals(son.getConfig(HelloEntity.MY_NAME), "Danny");
    }

}
