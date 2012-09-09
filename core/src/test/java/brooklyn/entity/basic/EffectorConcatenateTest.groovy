package brooklyn.entity.basic;

import static org.testng.Assert.*
import groovy.transform.InheritConstructors

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.Effector
import brooklyn.management.Task
import brooklyn.util.task.Tasks

public class EffectorConcatenateTest {

    
    private static final Logger log = LoggerFactory.getLogger(EffectorConcatenateTest.class);
    private static final long TIMEOUT = 10*1000
    
    @InheritConstructors
    public class MyEntity extends AbstractEntity {

        public static Effector<String> CONCATENATE = new MethodEffector<Void>(MyEntity.class, "concatenate");
    
        public MyEntity(Map flags) {
            super(flags)
        }

        AtomicReference concatTask = new AtomicReference();
        
        @Description("sample effector concatenating strings and sometimes waiting")
        String concatenate(@NamedParameter("first") @Description("first argument") String first,
                @NamedParameter("second") @Description("2nd arg") String second) {
            if ("wait".equals(first)) {
                // if first arg is wait, spawn a child,
                // then wait, setting task info from the second arg
                // (test will assert that status details are reported correctly)
                long startTime = System.currentTimeMillis();
                synchronized (concatTask) {
                    concatTask.set(Tasks.current());
                    concatTask.notifyAll();
                    Tasks.withBlockingDetails(second) {
                        concatTask.wait(TIMEOUT);
                    }
                    concatTask.set(null);
                }
                if (System.currentTimeMillis()-startTime >= TIMEOUT)
                    fail("took too long, probably wasn't notified");
            }
            return first+second
        }
    }
            
    @Test
    public void testCanInvokeEffector() {
        AbstractApplication app = new AbstractApplication() {}
        MyEntity e = new MyEntity([owner:app])
        
        // invocation map syntax
        Task<String> task = e.invoke(MyEntity.CONCATENATE, [first:"a",second:"b"])
        assertEquals(task.get(TIMEOUT, TimeUnit.MILLISECONDS), "ab")

        // method syntax
        assertEquals("xy", e.concatenate("x", "y"));
    }
    
    @Test
    public void testTaskReporting() {
        AbstractApplication app = new AbstractApplication() {}
        MyEntity e = new MyEntity([owner:app]);
        final AtomicReference<String> result = new AtomicReference<String>();

        new Thread({
            long startTime = System.currentTimeMillis();
            synchronized (e.concatTask) {
                try {
                    while (e.concatTask.get()==null) {
                        e.concatTask.wait(1000);
                        if (System.currentTimeMillis()-startTime >= TIMEOUT) {
                            result.set("took too long, probably wasn't notified");
                            return;
                        }
                    }

                    Task t = e.concatTask.get();
                    String status = t.getStatusDetail(true);
                    log.info("concat task says:\n"+status);
                    if (!status.startsWith("waiter, what's this fly doing")) {
                        result.set("Status not in expected format: doesn't start with blocking details\n"+status);
                        return;
                    }
                    // looks healthy
                    
                    // TODO add child above, and assert child details also included
                } finally {
                    e.concatTask.notifyAll();
                }
            }
        }).start();
    
        e.concatenate("wait", "waiter, what's this fly doing in my soup?");
        
        String problem = result.get();
        if (problem!=null) fail(problem);        
    }
    
}
