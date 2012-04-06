package brooklyn.event.adapter;

import static org.testng.Assert.assertEquals

import java.util.concurrent.Callable

import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.test.entity.TestEntity

public class FunctionSensorAdapterTest {

    final EntityLocal entity = new TestEntity();
    final SensorRegistry entityRegistry = new SensorRegistry(entity);

    FunctionSensorAdapter adapter;
    public FunctionSensorAdapter registerAdapter(FunctionSensorAdapter adapter=null) {
        if (adapter!=null) this.adapter = adapter;
        else adapter = this.adapter;
        adapter.pollPeriod = null;
        entityRegistry.register(adapter);
        entityRegistry.activateAdapters();
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
