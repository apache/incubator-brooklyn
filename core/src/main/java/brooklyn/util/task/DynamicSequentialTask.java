package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;

import com.google.common.collect.ImmutableList;

/** Represents a task whose run() method can create other tasks
 * which are run sequentially, but that sequence runs in parallel to this task 
 **/
public class DynamicSequentialTask<T> extends BasicTask<T> implements HasTaskChildren, TaskQueueingContext {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final Queue<Task<?>> secondaryJobsAll = new ConcurrentLinkedQueue<Task<?>>();
    protected final Queue<Task<?>> secondaryJobsRemaining = new ConcurrentLinkedQueue<Task<?>>();
    protected final Object jobTransitionLock = new Object();
    protected volatile boolean primaryStarted = false;
    protected volatile boolean primaryFinished = false;
    protected Thread primaryThread;
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public DynamicSequentialTask() {
        this(null);
    }
    
    public DynamicSequentialTask(Callable<T> mainJob) {
        this(MutableMap.of("tag", "compound"), mainJob);
    }
    
    public DynamicSequentialTask(Map<?,?> flags, Callable<T> mainJob) {
        super(flags);
        this.job = new DstJob(mainJob);
    }
    
    @Override
    public void queue(Task<?> t) {
        synchronized (jobTransitionLock) {
            if (primaryFinished)
                throw new IllegalStateException("Cannot add a task to "+this+" when it is already finished (trying to add "+t+")");
            secondaryJobsAll.add(t);
            secondaryJobsRemaining.add(t);
            ((TaskInternal<?>)t).markQueued();
            jobTransitionLock.notifyAll();
        }
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) return false;
        log.trace("cancelling {}", this);
        boolean cancel = super.cancel(mayInterruptIfRunning);
        for (Task<?> t: secondaryJobsAll)
            cancel |= t.cancel(mayInterruptIfRunning);
        if (primaryThread!=null) {
            log.trace("cancelling {} - interrupting", this);
            primaryThread.interrupt();
            cancel = true;
        }
        return cancel;
    }
    
    @Override
    public Iterable<Task<?>> getChildren() {
        return Collections.unmodifiableCollection(secondaryJobsAll);
    }
    
    /** submits the indicated task for execution in the current execution context, and returns immediately */
    protected void submitBackgroundInheritingContext(Task<?> task) {
        BasicExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (log.isTraceEnabled())
            log.trace("task {} - submitting background task {} ({})", new Object[] { 
                Tasks.current(), task, ec });
        if (ec==null) {
            String message = Tasks.current()!=null ?
                    // user forgot ExecContext:
                        "Task "+this+" submitting background task requires an ExecutionContext (an ExecutionManager is not enough): submitting "+task+" in "+Tasks.current()
                    : // should not happen:
                        "Cannot submit tasks inside DST when not in a task : submitting "+task+" in "+this;
            log.warn(message+" (rethrowing)");
            throw new IllegalStateException(message);
        }
        ec.submit(task);
    }

    protected class DstJob implements Callable<T> {
        protected Callable<T> primaryJob;
        
        public DstJob(Callable<T> mainJob) {
            this.primaryJob = mainJob;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T call() throws Exception {
            synchronized (jobTransitionLock) {
                primaryStarted = true;
                primaryThread = Thread.currentThread();
                for (Task<?> t: secondaryJobsAll)
                    ((TaskInternal<?>)t).markQueued();
            }
            // TODO overkill having a thread/task for this, but it works (room for optimization...)
            Task<List<Object>> secondaryJobMaster = Tasks.<List<Object>>builder().dynamic(false)
                    .name("DST manager (internal)")
                    .body(new Callable<List<Object>>() {
                @Override
                public List<Object> call() throws Exception {
                    List<Object> result = new ArrayList<Object>();
                    while (!primaryFinished || !secondaryJobsRemaining.isEmpty()) {
                        synchronized (jobTransitionLock) {
                            if (!primaryFinished && secondaryJobsRemaining.isEmpty()) {
                                jobTransitionLock.wait(1000);
                            }
                        }
                        @SuppressWarnings("rawtypes")
                        Task secondaryJob = secondaryJobsRemaining.poll();
                        if (secondaryJob != null) {
                            submitBackgroundInheritingContext(secondaryJob);
                            try {
                                result.add(secondaryJob.get());
                            } catch (Exception e) {
                                // secondary job queue aborts on error
                                if (log.isDebugEnabled())
                                    log.debug("Aborting secondary job queue for "+DynamicSequentialTask.this+" due to error in task "+secondaryJob+" ("+e+", being rethrown)");
                                for (Task<?> t: secondaryJobsRemaining)
                                    t.cancel(false);
                                throw e;
                            }
                        }
                    }
                    return result;
                }
            }).build();
            submitBackgroundInheritingContext(secondaryJobMaster);
            
            T result = null;
            try {
                try {
                    log.trace("calling primary job for {}", this);
                    if (primaryJob!=null) result = primaryJob.call();
                } finally {
                    log.trace("cleaning up for {}", this);
                    synchronized (jobTransitionLock) {
                        // semaphore might be nicer here (aled notes as it is this is a little hard to read)
                        primaryThread = null;
                        primaryFinished = true;
                        jobTransitionLock.notifyAll();
                    }
                    if (!isCancelled() && !secondaryJobMaster.isDone()) {
                        log.trace("waiting for secondaries for {}", this);
                        // wait on tasks sequentially so that blocking information is more interesting
                        DynamicTasks.waitForLast();
                        List<Object> result2 = secondaryJobMaster.get();
                        try {
                            if (primaryJob==null) result = (T)result2;
                        } catch (ClassCastException e) { /* ignore class cast exception; leave the result as null */ }
                    }
                }
            } catch (Throwable t) {
                handleException(t);
            }
            return result;
        }
    }

    @Override
    public List<Task<?>> getQueue() {
        return ImmutableList.copyOf(secondaryJobsAll);
    }

    public void handleException(Throwable throwable) throws Exception {
        // allow checked exceptions to be passed through
        if (throwable instanceof Exception) {
            if (throwable instanceof InterruptedException)
                throw new RuntimeInterruptedException((InterruptedException) throwable);
            throw (Exception)throwable;
        }
        throw Exceptions.propagate(throwable);
    }

    @Override
    public Task<?> last() {
        // TODO this is inefficient
        List<Task<?>> l = getQueue();
        if (l.isEmpty()) return null;
        return l.get(l.size()-1);
    }
    
}
