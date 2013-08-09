package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.ArrayList;
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

import com.google.common.base.Preconditions;
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
    
    public void queue(Task<?> t) {
        synchronized (jobTransitionLock) {
            if (primaryFinished)
                throw new IllegalStateException("Cannot add a task to "+this+" when it is already finished (trying to add "+t+")");
            secondaryJobsAll.add(t);
            secondaryJobsRemaining.add(t);
            ((BasicTask<?>)t).markQueued();
            jobTransitionLock.notifyAll();
        }
    }
    
    public Iterable<Task<?>> getChildren() {
        return secondaryJobsAll;
    }
    
    /** submits the indicated task for execution in the current execution context, and returns immediately */
    protected void submitBackgroundInheritingContext(Task<?> task) {
        BasicExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (log.isTraceEnabled())
            log.trace("task {} - submitting background task {} ({})", new Object[] { 
                Tasks.current(), task, ec });
        Preconditions.checkNotNull(ec,
                "Cannot submit tasks when not in the thread of a task with an execution context");
        ec.submit(task);
    }

    protected class DstJob implements Callable<T> {
        protected Callable<T> primaryJob;
        
        public DstJob(Callable<T> mainJob) {
            this.primaryJob = mainJob;
        }

        @Override
        public T call() throws Exception {
            synchronized (jobTransitionLock) {
                primaryStarted = true;
                for (Task<?> t: secondaryJobsAll)
                    ((BasicTask<?>)t).markQueued();
            }
            Task<List<Object>> secondaryJobMaster = new BasicTask<List<Object>>(new Callable<List<Object>>() {
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
                                throw e;
                            }
                        }
                    }
                    return result;
                }
            });
            submitBackgroundInheritingContext(secondaryJobMaster);
            
            T result = (primaryJob!=null ? primaryJob.call() : null);
            synchronized (jobTransitionLock) {
                // semaphore might be nicer here (aled notes as it is this is a little hard to read)
                primaryFinished = true;
                jobTransitionLock.notifyAll();
            }
            secondaryJobMaster.get();
            return result;
        }
    }

    @Override
    public List<Task<?>> getQueue() {
        return ImmutableList.copyOf(secondaryJobsAll);
    }

    @Override
    public Task<?> last() {
        // TODO this is inefficient
        List<Task<?>> l = getQueue();
        if (l.isEmpty()) return null;
        return l.get(l.size()-1);
    }
    
}
