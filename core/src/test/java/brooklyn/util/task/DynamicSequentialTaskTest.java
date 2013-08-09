package brooklyn.util.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DynamicSequentialTaskTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicSequentialTaskTest.class);
    
    BasicExecutionManager em;
    BasicExecutionContext ec;
    List<String> messages = new ArrayList<String>();
    Semaphore cancellations;


    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
        ec = new BasicExecutionContext(em);
        cancellations = new Semaphore(0);
        messages.clear();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
    }

    @Test
    public void testSimple() throws InterruptedException, ExecutionException {
        Callable<String> mainJob = new Callable<String>() {
            public String call() {
                log.info("main job - "+Tasks.current());
                messages.add("main");
                DynamicTasks.queue( sayTask("world", null) );
                return "bye";
            }            
        };
        DynamicSequentialTask<String> t = new DynamicSequentialTask<String>(mainJob);
        // this should be added before anything added when the task is invoked
        t.queue(sayTask("hello", null));
        
        Assert.assertEquals(messages, Lists.newArrayList());
        Assert.assertEquals(t.isBegun(), false);
        Assert.assertEquals(Iterables.size(t.getChildren()), 1);
        
        ec.submit(t);
        Assert.assertEquals(t.isSubmitted(), true);
        Assert.assertEquals(t.getUnchecked(Duration.ONE_SECOND), "bye");
        long elapsed = t.getEndTimeUtc() - t.getSubmitTimeUtc();
        Assert.assertTrue(elapsed < 1000, "elapsed time should have been less than 1s but was "+
                Time.makeTimeString(elapsed, true));
        Assert.assertEquals(Iterables.size(t.getChildren()), 2);
        Assert.assertEquals(messages.size(), 3, "expected 3 entries, but had "+messages);
        // either main or hello can be first, but world should be last 
        Assert.assertEquals(messages.get(2), "world");
    }
    
    public Callable<String> sayCallable(final String message, final Duration duration) {
        return new Callable<String>() {
            public String call() {
                log.info("will say "+message+" after "+duration);
                if (duration!=null && duration.toMilliseconds()>0) {
                    try {
                        Thread.sleep(duration.toMillisecondsRoundingAway());
                    } catch (InterruptedException e) {
                        cancellations.release();
                        throw Exceptions.propagate(e);
                    }
                }
                log.info("saying: "+message+ " - "+Tasks.current());
                synchronized (messages) {
                    messages.add(message);
                    messages.notifyAll();
                }
                return message;
            }            
        };
    }
    
    public Task<String> sayTask(String message, Duration duration) {
        return Tasks.<String>builder().body(sayCallable(message, duration)).build();
    }
    
    @Test
    public void testComplex() throws InterruptedException, ExecutionException {
        Task<List<?>> t = Tasks.sequential(
                sayTask("1", null),
                sayTask("2", null),
                Tasks.parallel(sayTask("4", Duration.millis(100)),
                        sayTask("3", null)),
                sayTask("5", null)
            );
        ec.submit(t);
        Assert.assertEquals(t.get().size(), 4); 
        Assert.assertEquals(new HashSet<Object>((List<?>)t.get().get(2)), MutableSet.of("3", "4"));
        Assert.assertEquals(messages, Arrays.asList("1", "2", "3", "4", "5"));
    }
    
    @Test
    public void testCancelled() throws InterruptedException, ExecutionException {
        Task<List<?>> t = Tasks.sequential(
                sayTask("1", null),
                sayTask("2", Duration.ONE_SECOND),
                sayTask("3", null));
        ec.submit(t);
        synchronized (messages) {
            while (messages.size()<=0)
                messages.wait();
        }
        Assert.assertEquals(messages, Arrays.asList("1"));
        Time.sleep(Duration.millis(50));
        t.cancel(true);
        Assert.assertTrue(t.isDone());
        // 2 should get cancelled, and invoke the cancellation semaphore
        // 3 should get cancelled and not run at all
        Assert.assertEquals(messages, Arrays.asList("1"));
        
        // we get one mutex from task2
        Assert.assertTrue(cancellations.tryAcquire(500, TimeUnit.MILLISECONDS));
        
        Iterator<Task<?>> ci = ((HasTaskChildren)t).getChildren().iterator();
        Assert.assertEquals(ci.next().get(), "1");
        Task<?> task2 = ci.next();
        Assert.assertTrue(task2.isBegun());
        Assert.assertTrue(task2.isDone());
        Assert.assertTrue(task2.isCancelled());
        
        Task<?> task3 = ci.next();
        Assert.assertFalse(task3.isBegun());
        Assert.assertTrue(task2.isDone());
        Assert.assertTrue(task2.isCancelled());
        
        // but we do _not_ get a mutex from task3 as it does not run (is not interrupted)
        Assert.assertEquals(cancellations.availablePermits(), 0);
    }

}
