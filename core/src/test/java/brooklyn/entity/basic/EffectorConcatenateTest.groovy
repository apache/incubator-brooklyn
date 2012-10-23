package brooklyn.entity.basic;

import static org.testng.Assert.*
import groovy.transform.InheritConstructors

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.Effector
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.task.BasicExecutionContext
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
        // FIXME instead of waiting on this we should use semaphores -- seems we very occasionally get spurious wakes
        AtomicReference response = new AtomicReference();
        
        @Description("sample effector concatenating strings and sometimes waiting")
        String concatenate(@NamedParameter("first") @Description("first argument") String first,
                @NamedParameter("second") @Description("2nd arg") String second) {
            if ("wait".equals(first)) {
                // if first arg is wait, spawn a child, then wait
                BasicExecutionContext.getCurrentExecutionContext().submit(
                    displayName: "SarcyResponse",
                    {
                        log.info("beginning scary response "+Tasks.current()+", with tags "+Tasks.current().tags)
                        synchronized (response) {
                            Tasks.setBlockingDetails("looks like the backstroke to me");
                            response.notifyAll();
                            response.wait(TIMEOUT);
                        }
                    });
                
                Tasks.setExtraStatusDetails("What's the soup du jour? That's the soup of the day!");
                
                // wait, setting task info from the second arg
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
        new LocalManagementContext().manage(app);
        
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
        new LocalManagementContext().manage(app);
        
        final AtomicReference<String> result = new AtomicReference<String>();

        Thread bg = new Thread({
            try {
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
                            result.set("Status not in expected format: doesn't start with blocking details 'waiter...'\n"+status);
                            return;
                        }
                        if (!status.contains("du jour")) {
                            result.set("Status not in expected format: doesn't contain extra status details phrase 'du jour'\n"+status);
                            return;
                        }
                        // looks healthy
                    } finally {
                        e.concatTask.notifyAll();
                    }
                }

                ExecutionManager em = e.getExecutionContext().getExecutionManager();
                synchronized (e.response) {
                    Task reply=null;
                    while (reply==null) {
                        Collection<Task> entityTasks = em.getTasksWithTag(e);
                        log.info("entity "+e+" running: "+entityTasks);
                        reply = entityTasks.find { Task t -> t.displayName=="SarcyResponse" }
                        if (reply!=null) break;
                        if (System.currentTimeMillis()-startTime >= TIMEOUT) {
                            result.set("response took too long, probably wasn't notified");
                            return;
                        }
                        e.response.wait(TIMEOUT);
                    }
                    String status = reply.getStatusDetail(true);
                    log.info("reply task says:\n"+status);
                    if (!status.contains("backstroke")) {
                        result.set("Status not in expected format: doesn't contain blocking details phrase 'backstroke'\n"+status);
                        return;
                    }
                    e.response.notifyAll();
                }
            } catch (Throwable t) {
                log.warn("Failure: "+t, t);
                result.set("Failure: "+t);
            }
        });
        bg.start();
    
        e.concatenate("wait", "waiter, what's this fly doing in my soup?");
        
        bg.join();
        String problem = result.get();
        if (problem!=null) fail(problem);
    }
    
}
