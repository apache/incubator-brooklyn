package brooklyn.management.internal.task;

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import brooklyn.util.JavaGroovyEquivalents;


class DelegatingExecutor implements Executor {
	
    ExecutorService executor = Executors.newCachedThreadPool();
    
    TimeDuration timeout = null;
    Runnable preTask=null, postTask=null;

    public DelegatingExecutor(Map<String,?> properties) {
        this.timeout = JavaGroovyEquivalents.toTimeDuration(properties.get("preTask"));
        this.preTask = (Runnable) properties.get("preTask");
        this.postTask = (Runnable) properties.get("postTask");
    }
    
    public void execute(final Runnable command) {
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (preTask != null) preTask.run();
                    command.run();
                } finally {
                    if (postTask != null) postTask.run();
                }
            }
        });
    }
    
    public void shutdown() {
        executor.shutdown();
    }
 
    public void shutdownNow() {
        executor.shutdownNow();
    }

    public void preTask() {}
    public void postTask() {}
        
    public <T> Callable<T> asWrappedCallable(final Callable<T> c) {
        return new Callable<T>() {
            public T call() throws Exception {
                try {
                    if (preTask != null) preTask.run();
                    return c.call();
                } finally { 
                    if (postTask != null) postTask.run();
                } 
            }
        };
    }
    
    public List<Future<?>> executeAll(final Closure<?>... tasks) throws InterruptedException {
        Collection<Callable<Object>> wrapped = new ArrayList<Callable<Object>>(tasks.length);
        for (Closure<?> task : tasks) wrapped.add((Callable)asWrappedCallable(task));
        
        if (timeout != null)
            return (List) executor.invokeAll(wrapped, timeout.toMilliseconds(), TimeUnit.MILLISECONDS);
        else
            return (List) executor.invokeAll(wrapped);
    }
}
