package brooklyn.management.internal.task

import groovy.lang.Closure
import groovy.time.TimeDuration

import java.util.List
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import brooklyn.management.ExecutionManager

class DelegatingExecutor implements Executor {
    ExecutorService executor = Executors.newCachedThreadPool()
    TimeDuration timeout = null;
    Closure<?> preTask=null, postTask=null;
    
    public void execute(Runnable command) {
        executor.execute {
            try {
                preTask?.run()
                command.run()
            } finally {
                postTask?.run()
            }
        }
    }
    
    public void shutdown() {
        executor.shutdown()
    }
 
    public void shutdownNow() {
        executor.shutdownNow()
    }

    public void preTask() {}
    public void postTask() {}
        
    public Callable asWrappedCallable(final Closure c) {
        return new Callable() {
            public Object call() throws Exception {
                try {
                    preTask?.run()
                    return c.run()
                } finally { postTask?.run() } 
            }
        };
    }
    
    public List<Future<?>> executeAll(final Closure...c) {
        if (timeout)
            executor.invokeAll(c.collect { asWrappedCallable(it) }, timeout.toMilliseconds(), TimeUnit.MILLISECONDS)
        else
            executor.invokeAll(c.collect { asWrappedCallable(it) })
    }
}
