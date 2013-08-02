package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Preconditions;

/** Represents a task whose run() method can create other tasks
 * which are run sequentially, but that sequence runs in parallel to this task 
 **/
public class DynamicSequentialTask<T> extends BasicTask<T> implements HasTaskChildren {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected T result;

    @SuppressWarnings("rawtypes")
    protected final Queue<Task> secondaryJobsAll = new ConcurrentLinkedQueue<Task>();
    @SuppressWarnings("rawtypes")
    protected final Queue<Task> secondaryJobsRemaining = new ConcurrentLinkedQueue<Task>();
    protected final AtomicBoolean primaryJobFinished = new AtomicBoolean(false);
    
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
    
    public DynamicSequentialTask(Map<String,?> flags, Callable<T> mainJob) {
        super(flags);
        this.job = new DstJob(mainJob);
    }
    
    // TODO make an interface
    public void addTask(Task<?> t) {
        synchronized (primaryJobFinished) {
            if (primaryJobFinished.get())
                throw new IllegalStateException("Cannot add a task to "+this+" when it is already finished (trying to add "+t+")");
            secondaryJobsAll.add(t);
            secondaryJobsRemaining.add(t);
            primaryJobFinished.notifyAll();
        }
    }
    
    @SuppressWarnings("rawtypes")
    public Iterable<Task> getChildrenTasks() {
        return secondaryJobsAll;
    }
    
    /** submits the indicated task for execution in the current execution context, and returns immediately */
    protected void submitBackgroundInheritingContext(Task<?> task) {
        // TODO should get flags etc from parent task (I think)
        // (at least some way to indicate it is a subtask?)
//        task.setParent(Tasks.current());
        // (or do this in addTask?)
        if (log.isTraceEnabled())
            log.trace("task {} - submitting background task {} ({})", new Object[] { 
                Tasks.current(), task, BasicExecutionContext.getCurrentExecutionContext() });
        Preconditions.checkNotNull(BasicExecutionContext.getCurrentExecutionContext(),
                "Cannot submit tasks when not in the thread of a task with an execution context")
                .submit(task);
    }

    protected class DstJob implements Callable<T> {
        protected Callable<T> primaryJob;
        
        public DstJob(Callable<T> mainJob) {
            this.primaryJob = mainJob;
        }

        @Override
        public T call() throws Exception {
            Task<List<Object>> secondaryJobMaster = new BasicTask<List<Object>>(new Callable<List<Object>>() {
                @Override
                public List<Object> call() throws Exception {
                    List<Object> result = new ArrayList<Object>();
                    while (!primaryJobFinished.get() || !secondaryJobsRemaining.isEmpty()) {
                        synchronized (primaryJobFinished) {
                            if (!primaryJobFinished.get() && secondaryJobsRemaining.isEmpty()) {
                                primaryJobFinished.wait(1000);
                            }
                        }
                        @SuppressWarnings("rawtypes")
                        Task secondaryJob = secondaryJobsRemaining.poll();
                        if (secondaryJob != null) {
                            submitBackgroundInheritingContext(secondaryJob);
                            try {
                                result.add(secondaryJob.get());
                            } catch (Exception e) {
                                throw e;
                            }
                        }
                    }
                    return result;
                }
            });
            submitBackgroundInheritingContext(secondaryJobMaster);
            
            T result = (primaryJob!=null ? primaryJob.call() : null);
            synchronized (primaryJobFinished) {
                primaryJobFinished.set(true);
                primaryJobFinished.notifyAll();
            }
            secondaryJobMaster.get();
            return result;
        }
    }
    
}
