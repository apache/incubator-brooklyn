package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class EffectorConcatenateTest {

    
    private static final Logger log = LoggerFactory.getLogger(EffectorConcatenateTest.class);
    private static final long TIMEOUT = 10*1000;
    
    public static class MyEntity extends AbstractEntity {

        public static Effector<String> CONCATENATE = new MethodEffector<String>(MyEntity.class, "concatenate");
    
        public MyEntity() {
            super();
        }
        public MyEntity(Map flags) {
            super(flags);
        }
        public MyEntity(Entity parent) {
            super(parent);
        }
        public MyEntity(Map flags, Entity parent) {
            super(flags, parent);
        }

        AtomicReference<Task<?>> concatTask = new AtomicReference<Task<?>>();
        // FIXME instead of waiting on this we should use semaphores -- seems we very occasionally get spurious wakes
        AtomicReference<String> response = new AtomicReference<String>();
        
        @Description("sample effector concatenating strings and sometimes waiting")
        public String concatenate(@NamedParameter("first") @Description("first argument") String first,
                @NamedParameter("second") @Description("2nd arg") String second) throws Exception {
            if ("wait".equals(first)) {
                // if first arg is wait, spawn a child, then wait
                BasicExecutionContext.getCurrentExecutionContext().submit(
                        MutableMap.of("displayName", "SarcyResponse"),
                        new Callable<Void>() {
                            public Void call() throws Exception {
                                log.info("beginning scary response "+Tasks.current()+", with tags "+Tasks.current().getTags());
                                synchronized (response) {
                                    Tasks.setBlockingDetails("looks like the backstroke to me");
                                    response.notifyAll();
                                    response.wait(TIMEOUT);
                                }
                                return null;
                            }});
                
                Tasks.setExtraStatusDetails("What's the soup du jour? That's the soup of the day!");
                
                // wait, setting task info from the second arg
                // (test will assert that status details are reported correctly)
                long startTime = System.currentTimeMillis();
                synchronized (concatTask) {
                    concatTask.set(Tasks.current());
                    concatTask.notifyAll();
                    Tasks.withBlockingDetails(second, new Callable<Void>() {
                        public Void call() throws Exception {
                            concatTask.wait(TIMEOUT);
                            return null;
                        }});
                    concatTask.set(null);
                }
                if (System.currentTimeMillis()-startTime >= TIMEOUT)
                    fail("took too long, probably wasn't notified");
            }
            return first+second;
        }
    }
            
    private Application app;
    private MyEntity e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplication();
        e = new MyEntity(app);
        Entities.startManagement(app);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
    }
    
    @Test
    public void testCanInvokeEffector() throws Exception {
        // invocation map syntax
        Task<String> task = e.invoke(MyEntity.CONCATENATE, ImmutableMap.of("first", "a", "second", "b"));
        assertEquals(task.get(TIMEOUT, TimeUnit.MILLISECONDS), "ab");

        // method syntax
        assertEquals("xy", e.concatenate("x", "y"));
    }
    
    @Test
    public void testTaskReporting() throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();

        Thread bg = new Thread(new Runnable() {
            public void run() {
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
    
                    ExecutionManager em = e.getManagementContext().getExecutionManager();
                    synchronized (e.response) {
                        Task reply=null;
                        while (reply==null) {
                            Collection<Task<?>> entityTasks = em.getTasksWithTag(e);
                            log.info("entity "+e+" running: "+entityTasks);
                            Iterable<Task<?>> matchingTasks = Iterables.filter(entityTasks, new Predicate<Task<?>>() {
                                public boolean apply(Task<?> input) {
                                    return "SarcyResponse".equals(input.getDisplayName());
                                }
                            });
                            reply = Iterables.getFirst(matchingTasks, null);
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
            }});
        bg.start();
    
        e.invoke(MyEntity.CONCATENATE, ImmutableMap.of("first", "wait", "second", "waiter, what's this fly doing in my soup?")).get();
        
        bg.join();
        String problem = result.get();
        if (problem!=null) fail(problem);
    }
}
