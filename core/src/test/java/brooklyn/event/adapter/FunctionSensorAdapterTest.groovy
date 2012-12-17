package brooklyn.event.adapter;

import static org.testng.Assert.assertEquals

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestEntity
import java.util.concurrent.TimeUnit

import brooklyn.test.entity.TestApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import brooklyn.test.TestUtils

public class FunctionSensorAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(FunctionSensorAdapterTest.class)

    TestApplication app;
    EntityLocal entity;
    SensorRegistry entityRegistry;

    @BeforeMethod
    public void setup() {
        app = new TestApplication();
        entity = new TestEntity(app);
        entityRegistry = new SensorRegistry(entity);
        app.startManagement();
    }
    
    FunctionSensorAdapter adapter;

    public FunctionSensorAdapter registerAdapter(FunctionSensorAdapter adapter=null, boolean clearPollPeriod=true) {
        if (adapter!=null) this.adapter = adapter;
        else adapter = this.adapter;
        if (clearPollPeriod) adapter.pollPeriod = null;
        entityRegistry.register(adapter);
        adapter;
    }
    
    int count = 0;
    public int inc() { ++count; }
    
    @Test
    public void testSimpleFunction() {
        adapter = new FunctionSensorAdapter(this.&inc);
        registerAdapter();
        
        count = 9;
        
        adapter.poll(TestEntity.SEQUENCE);
        
        adapter.poller.executePoll();
        assertEquals(entity.getAttribute(TestEntity.SEQUENCE), 10);
    }

    @Test
     public void testWithPeriod() {
         entity.setAttribute(TestEntity.SEQUENCE,0);
         adapter = new FunctionSensorAdapter(period: 200*TimeUnit.MILLISECONDS, {1});
         registerAdapter(adapter, false);
         adapter.poll(TestEntity.SEQUENCE);
         app.start([]);
         TestUtils.assertEventually(timeout: 5*TimeUnit.SECONDS) {
            assertEquals(new Integer(1), entity.getAttribute(TestEntity.SEQUENCE))
         }
     }

    @Test
    public void testChainedFunction() {
        registerAdapter(new FunctionSensorAdapter(this.&inc)).
            then({ 2*it }).
                poll(TestEntity.SEQUENCE);
                
        count = 11;
        
        adapter.poller.executePoll();
        assertEquals(entity.getAttribute(TestEntity.SEQUENCE), 24);
    }
    
    public int square(int x) { x*x }

    public static final BasicAttributeSensor<String> EXTRA_NAME = [ String, "test.extra.name", "Test Extra name" ]
    
    @Test
    public void testFunctionWith() {
        adapter = registerAdapter(new FunctionSensorAdapter());
        
        adapter.then({inc()}).with {
            poll(TestEntity.SEQUENCE);
            then({"try${it}"}).poll(TestEntity.NAME);
            //double chained and implicit conversion int-to-string
            then(this.&square).then({square(it)}).poll(EXTRA_NAME);
        }
        
        count = 2;
        
        adapter.poller.executePoll();
        assertEquals(entity.getAttribute(TestEntity.SEQUENCE), 3);
        assertEquals(entity.getAttribute(TestEntity.NAME), "try3");
        assertEquals(entity.getAttribute(EXTRA_NAME), "81");
    }

    Map takeSample() {
        Map result = [:]
        result.seq = inc();
        result.one = 1;
        result;
    }

    /* this is probably the most interesting/useful syntax example */    
    @Test
    public void testFunctionWithWithAndClosureOnPoll() {
        registerAdapter(new FunctionSensorAdapter(this.&takeSample)).with {
            poll(TestEntity.SEQUENCE, { it.seq });
            then({square(it.seq * it.one)}).with {
                then({"try${it}"}).poll(TestEntity.NAME);
                poll(EXTRA_NAME, this.&square);
                //double chained and implicit conversion int-to-string
            }
        }
        
        count = 1;
        
        adapter.poller.executePoll();
        assertEquals(entity.getAttribute(TestEntity.SEQUENCE), 2);
        assertEquals(entity.getAttribute(TestEntity.NAME), "try4");
        assertEquals(entity.getAttribute(EXTRA_NAME), "16");
    }

}