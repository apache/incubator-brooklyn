package brooklyn.util.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import brooklyn.management.Task;
import brooklyn.util.time.Time;

public class DynamicSequentialTaskTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicSequentialTaskTest.class);
    
    List<String> messages = new ArrayList<String>();
    
    @Test
    public void testSimple() throws InterruptedException, ExecutionException {
        BasicExecutionManager em = new BasicExecutionManager("tests");
        BasicExecutionContext ec = new BasicExecutionContext(em);
        
        Callable<String> mainJob = new Callable<String>() {
            public String call() {
                log.info("main job - "+Tasks.current());
                messages.add("main");
                say("world");
                return "bye";
            }            
        };
        DynamicSequentialTask<String> t = new DynamicSequentialTask<String>(mainJob);
        // this should be added before anything added when the task is invoked
        t.queue(sayTask("hello"));
        
        Assert.assertEquals(messages, Lists.newArrayList());
        Assert.assertEquals(t.isBegun(), false);
        Assert.assertEquals(Iterables.size(t.getChildren()), 1);
        
        ec.submit(t);
        Assert.assertEquals(t.isSubmitted(), true);
        Assert.assertEquals(t.get(), "bye");
        long elapsed = t.getEndTimeUtc() - t.getSubmitTimeUtc();
        Assert.assertTrue(elapsed < 1000, "elapsed time should have been less than 1s but was "+
                Time.makeTimeString(elapsed, true));
        Assert.assertEquals(Iterables.size(t.getChildren()), 2);
        Assert.assertEquals(messages.size(), 3, "expected 3 entries, but had "+messages);
        // either main or hello can be first, but world should be last 
        Assert.assertEquals(messages.get(2), "world");
    }
    
    public Runnable sayRunnable(final String message) {
        return new Runnable() {
            public void run() {
                log.info("saying: "+message+ " - "+Tasks.current());
                messages.add(message);
            }            
        };
    }
    
    public Task<Void> sayTask(final String message) {
        return new BasicTask<Void>(sayRunnable(message));
    }
    
    public Task<Void> say(final String message) {
        Task<Void> result = sayTask(message);
        ContextTasks.add(result);
        return result;
    }
    
}
