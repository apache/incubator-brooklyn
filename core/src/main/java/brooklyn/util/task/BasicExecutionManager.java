package brooklyn.util.task;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.text.Identifiers;

import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * TODO javadoc
 */
public class BasicExecutionManager implements ExecutionManager {
    private static final Logger log = LoggerFactory.getLogger(BasicExecutionManager.class);

    /**
     * Renaming threads can really helps with debugging etc; however it's a massive performance hit (2x)
     * <p>
     * We get 55000 tasks per sec with this off, 28k/s with this on.
     * <p>
     * (In old Groovy version btw we could run 6500/s vs 2300/s with renaming, from a single thread.) 
     * <p>
     * Defaults to false if system property is not set.
     */
    private static final boolean RENAME_THREADS = Boolean.parseBoolean(System.getProperty("brooklyn.executionManager.renameThreads"));
    
    private static class PerThreadCurrentTaskHolder {
        public static final ThreadLocal<Task> perThreadCurrentTask = new ThreadLocal<Task>();
    }

    public static ThreadLocal<Task> getPerThreadCurrentTask() {
        return PerThreadCurrentTaskHolder.perThreadCurrentTask;
    }

    /** @deprecated in 0.4.0, use Tasks.current() */
    public static Task getCurrentTask() { return Tasks.current(); }

    /** convenience for setting "blocking details" on any task where the current thread is running;
     * typically invoked prior to a wait, for transparency to a user;
     * then invoked with 'null' just after the wait 
     * 
     * @deprecated in 0.4.0, use Tasks.setBlockingDetails */
    public static void setBlockingDetails(String description) {
        Tasks.setBlockingDetails(description);
    }
    /** convenience for setting "blocking details" on any task where the current thread is running,
     * while the passed code is executed; often used from groovy as
     * <pre>{@code withBlockingDetails("sleeping 5s") { Thread.sleep(5000); } }</pre> 
     * 
     * @deprecated in 0.4.0, use Tasks.withBlockingDetails */
    public static Object withBlockingDetails(String description, Callable code) throws Exception {
        return Tasks.withBlockingDetails(description, code);
    }

    private final ThreadFactory threadFactory;
    
    private final ThreadFactory daemonThreadFactory;
    
    private final ExecutorService runner;
        
	private final ScheduledExecutorService delayedRunner;
	
    // TODO Could have a set of all knownTasks; but instead we're having a separate set per tag,
    // so the same task could be listed multiple times if it has multiple tags...

    //access to the below is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
    //(but more testing is needed before we are sure it is thread-safe!)
    //synch blocks are as finely grained as possible for efficiency
    //Not using a CopyOnWriteArraySet for each, because profiling showed this being a massive perf bottleneck.
    private ConcurrentMap<Object,Set<Task>> tasksByTag = new ConcurrentHashMap<Object,Set<Task>>();
    
    private ConcurrentMap<String,Task> tasksById = new ConcurrentHashMap<String,Task>();

    @Deprecated
    private ConcurrentMap<Object, TaskPreprocessor> preprocessorByTag = new ConcurrentHashMap<Object, TaskPreprocessor>();

    private ConcurrentMap<Object, TaskScheduler> schedulerByTag = new ConcurrentHashMap<Object, TaskScheduler>();
    
    private final AtomicLong totalTaskCount = new AtomicLong();
    
    private final AtomicInteger incompleteTaskCount = new AtomicInteger();
    
    private final AtomicInteger activeTaskCount = new AtomicInteger();
    
    private final List<ExecutionListener> listeners = new CopyOnWriteArrayList<ExecutionListener>();
    
    public BasicExecutionManager(String contextid) {
        threadFactory = newThreadFactory(contextid);
        daemonThreadFactory = new ThreadFactoryBuilder()
                .setThreadFactory(threadFactory)
                .setDaemon(true)
                .build();
                
        // use Executors.newCachedThreadPool(daemonThreadFactory), but timeout of 1s rather than 60s for better shutdown!
        runner = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), 
                daemonThreadFactory);
            
        delayedRunner = new ScheduledThreadPoolExecutor(1, daemonThreadFactory);
    }
    
	/** 
	 * For use by overriders to use custom thread factory.
	 * But be extremely careful: called by constructor, so before sub-class' constructor will
	 * have been invoked!
	 */
	protected ThreadFactory newThreadFactory(String contextid) {
	    return new ThreadFactoryBuilder()
        	    .setNameFormat("brooklyn-execmanager-"+contextid+"-%d")
        	    .setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
                            log.error("Uncaught exception in thread "+t.getName(), e);
                        }})
                .build();
	}
	
    public void shutdownNow() {
        runner.shutdownNow();
    }
    
    public void addListener(ExecutionListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(ExecutionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Deletes the given tag, including all tasks using this tag.
     * 
     * Useful, for example, if an entity is being expunged so that we don't keep holiding
     * a reference to it as a tag.
     */
    public void deleteTag(Object tag) {
        Set<Task> tasks = tasksByTag.remove(tag);
        if (tasks != null) {
            for (Task task : tasks) {
                deleteTask(task);
            }
        }
    }

    public void deleteTask(Task<?> task) {
        Set<?> tags = checkNotNull((BasicTask<?>)task, "task").tags;
        if (tags != null) {
            for (Object tag : tags) {
                Set<Task> tasks = getMutableTasksWithTagOrNull(tag);
                if (tasks != null) tasks.remove(task);
            }
        }
        tasksById.remove(task.getId());
    }

    public boolean isShutdown() {
        return runner.isShutdown();
    }
    
    public long getTotalTasksSubmitted() {
        return totalTaskCount.get();
    }
    
    public long getNumIncompleteTasks() {
        return incompleteTaskCount.get();
    }
    
    public long getNumActiveTasks() {
        return activeTaskCount.get();
    }

    public long getNumInMemoryTasks() {
        return tasksById.size();
    }

    private Set<Task> getMutableTasksWithTag(Object tag) {
        if (tag == null) {
            System.out.println("argph, null");
        }
        tasksByTag.putIfAbsent(tag, Collections.synchronizedSet(new LinkedHashSet<Task>()));
        return tasksByTag.get(tag);
    }

    private Set<Task> getMutableTasksWithTagOrNull(Object tag) {
        return tasksByTag.get(tag);
    }

    @Override
    public Task getTask(String id) {
        return tasksById.get(id);
    }
    
    @Override
    public Set<Task<?>> getTasksWithTag(Object tag) {
        Set<Task> result = getMutableTasksWithTag(tag);
        synchronized (result) {
            return (Set)Collections.unmodifiableSet(new LinkedHashSet<Task>(result));
        }
    }
    
    @Override
    public Set<Task<?>> getTasksWithAnyTag(Iterable<?> tags) {
        Set result = new LinkedHashSet<Task>();
        Iterator ti = tags.iterator();
        while (ti.hasNext()) {
            result.addAll(getTasksWithTag(ti.next()));
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<Task<?>> getTasksWithAllTags(Iterable<?> tags) {
        //NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
        //by first looking for the least-used tag, getting those tasks, and then for each of those tasks
        //checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
        Set result = new LinkedHashSet<Task>();
        boolean first = true;
        Iterator ti = tags.iterator();
        while (ti.hasNext()) {
            Object tag = ti.next();
            if (first) { 
                first = false;
                result.addAll(getTasksWithTag(tag));
            } else {
                result.retainAll(getTasksWithTag(tag));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Object> getTaskTags() { return Collections.unmodifiableSet(Sets.newLinkedHashSet(tasksByTag.keySet())); }

    public Task<?> submit(Runnable r) { return submit(new LinkedHashMap(1), r); }
    public Task<?> submit(Map<?,?> flags, Runnable r) { return submit(flags, new BasicTask(flags, r)); }

    public Task<?> submit(Callable c) { return submit(new LinkedHashMap(1), c); }
    public <T> Task<T> submit(Map<?,?> flags, Callable<T> c) { return submit(flags, new BasicTask<T>(flags, c)); }

    public <T> Task<T> submit(Task<T> t) { return submit(new LinkedHashMap(1), t); }
    public <T> Task<T> submit(Map<?,?> flags, Task<T> task) {
        synchronized (task) {
            if (((BasicTask)task).getResult()!=null) return task;
            return submitNewTask(flags, task);
        }
    }

    public <T> Task<T> submit(Map<?,?> flags, Object c) {
        if (c instanceof Task) {
            return submit(flags, (Task)c);
        } else if (c instanceof Callable) {
            return submit(flags, (Callable)c);
        } else if (c instanceof Runnable) {
            return (Task<T>) submit(flags, (Runnable)c);
        } else {
            throw new IllegalArgumentException("Unhandled task type: c="+c+"; type="+(c!=null ? c.getClass() : "null"));
        }
    }

    public <T> Task<T> scheduleWith(Task<T> task) { return scheduleWith(Collections.emptyMap(), task); }
	public <T> Task<T> scheduleWith(Map flags, Task<T> task) {
		synchronized (task) {
			if (((BasicTask)task).getResult()!=null) return task;
			return submitNewTask(flags, task);
		}
	}

	protected Task submitNewScheduledTask(final Map flags, final ScheduledTask task) {
		task.submitTimeUtc = System.currentTimeMillis();
		tasksById.put(task.getId(), task);
		if (!task.isDone()) {
			task.result = delayedRunner.schedule(new Callable() { public Object call() {
				if (task.startTimeUtc==-1) task.startTimeUtc = System.currentTimeMillis();
				final BasicTask taskScheduled = (BasicTask) task.newTask();
				taskScheduled.submittedByTask = task;
				final Callable oldJob = taskScheduled.job;
				taskScheduled.job = new Callable() { public Object call() {
					task.recentRun = taskScheduled;
					synchronized (task) {
					    task.notifyAll();
					}
					Object result;
					try {
					    result = oldJob.call();
					} catch (Exception e) {
					    log.warn("Error executing "+oldJob+" ("+task.getDescription()+")", e);
					    throw Throwables.propagate(e);
				    }
					task.runCount++;
					if (task.period!=null && !task.isCancelled()) {
						task.delay = task.period;
						submitNewScheduledTask(flags, task);
					}
					return result;
				}};
				task.nextRun = taskScheduled;
				return submit(taskScheduled);
			}},
			task.delay.toNanoseconds(), TimeUnit.NANOSECONDS);
		} else {
			task.endTimeUtc = System.currentTimeMillis();
		}
		return task;
	}

    protected <T> Task<T> submitNewTask(final Map flags, final Task<T> task) {
        if (task instanceof ScheduledTask)
            return submitNewScheduledTask(flags, (ScheduledTask)task);
        
        tasksById.put(task.getId(), task);
        totalTaskCount.incrementAndGet();
        
        beforeSubmit(flags, task);
        
        if (((BasicTask)task).job==null) 
            throw new NullPointerException("Task "+task+" submitted with with null job: job must be supplied.");
        
        Callable job = new Callable() { public Object call() {
            Object result = null;
            Throwable error = null;
            String oldThreadName = Thread.currentThread().getName();
            try {
                if (RENAME_THREADS) {
                    String newThreadName = oldThreadName+"-"+task.getDisplayName()+
                            "["+task.getId().substring(0, 8)+"]";
                    Thread.currentThread().setName(newThreadName);
                }
                beforeStart(flags, task);
                if (!task.isCancelled()) {
                    result = ((BasicTask)task).job.call();
                } else throw new CancellationException();
            } catch(Throwable e) {
                error = e;
            } finally {
                if (RENAME_THREADS) {
                    Thread.currentThread().setName(oldThreadName);
                }
                afterEnd(flags, task);
            }
            if (error!=null) {
                log.warn("Error while running task "+task+" (rethrowing): "+error.getMessage(), error);
                throw Throwables.propagate(error);
            }
            return result;
        }};
        ((BasicTask)task).initExecutionManager(this);
        
        // If there's a scheduler then use that; otherwise execute it directly
        Set<TaskScheduler> schedulers = null;
        for (Object tago: ((BasicTask)task).tags) {
            TaskScheduler scheduler = getTaskSchedulerForTag(tago);
            if (scheduler!=null) {
                if (schedulers==null) schedulers = new LinkedHashSet(2);
                schedulers.add(scheduler);
            }
        }
        Future future;
        if (schedulers!=null && !schedulers.isEmpty()) {
			if (schedulers.size()>1) log.warn("multiple schedulers detected, using only the first, for "+task+": "+schedulers);
            future = schedulers.iterator().next().submit(job);
        } else {
            future = runner.submit(job);
        }

        ((BasicTask)task).initResult(future);
        return task;
    }
    
    @SuppressWarnings("deprecation")
    protected void beforeSubmit(Map flags, Task<?> task) {
        incompleteTaskCount.incrementAndGet();
        
		Task currentTask = getCurrentTask();
        if (currentTask!=null) ((BasicTask)task).submittedByTask = currentTask;
        ((BasicTask)task).submitTimeUtc = System.currentTimeMillis();
        
        if (flags.get("tag")!=null) ((BasicTask)task).tags.add(flags.remove("tag"));
        if (flags.get("tags")!=null) ((BasicTask)task).tags.addAll((Collection)flags.remove("tags"));

        for (Object tag: ((BasicTask)task).tags) {
            getMutableTasksWithTag(tag).add(task);
        }
        
        List tagLinkedPreprocessors = new ArrayList();
        for (Object tag: ((BasicTask)task).tags) {
            TaskPreprocessor p = getTaskPreprocessorForTag(tag);
            if (p!=null) tagLinkedPreprocessors.add(p);
        }
        flags.put("tagLinkedPreprocessors", tagLinkedPreprocessors);
        for (Object ppo: tagLinkedPreprocessors) { 
            TaskPreprocessor t = (TaskPreprocessor)ppo;
            t.onSubmit(flags, task); 
        }
    }

    @SuppressWarnings("deprecation")
    protected void beforeStart(Map flags, Task<?> task) {
        activeTaskCount.incrementAndGet();
        
        //set thread _before_ start time, so we won't get a null thread when there is a start-time
        if (log.isTraceEnabled()) log.trace(""+this+" beforeStart, task: "+task);
        if (!task.isCancelled()) {
            ((BasicTask)task).thread = Thread.currentThread();
            if (RENAME_THREADS) {
                String newThreadName = "brooklyn-" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, task.getDisplayName().replace(" ", "")) + "-" + task.getId().substring(0, 8);
                ((BasicTask)task).thread.setName(newThreadName);
            }
            PerThreadCurrentTaskHolder.perThreadCurrentTask.set(task);
            ((BasicTask)task).startTimeUtc = System.currentTimeMillis();
        }
        for (Object to : (Collection)flags.get("tagLinkedPreprocessors")) { 
            TaskPreprocessor t = (TaskPreprocessor)to;
            t.onStart(flags, task); 
        }
        ExecutionUtils.invoke(flags.get("newTaskStartCallback"), task);
    }

    @SuppressWarnings("deprecation")
    protected void afterEnd(Map flags, Task<?> task) {
        activeTaskCount.decrementAndGet();
        incompleteTaskCount.decrementAndGet();

        if (log.isTraceEnabled()) log.trace(this+" afterEnd, task: "+task);
        ExecutionUtils.invoke(flags.get("newTaskEndCallback"), task);
        List l = (List)flags.get("tagLinkedPreprocessors");
        Collections.reverse(l);
        for (Object li: l) {
            TaskPreprocessor t = (TaskPreprocessor)li;
            t.onEnd(flags, task); 
        }

        PerThreadCurrentTaskHolder.perThreadCurrentTask.remove();
        ((BasicTask)task).endTimeUtc = System.currentTimeMillis();
        //clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
        if (RENAME_THREADS) {
            String newThreadName = "brooklyn-"+Identifiers.makeRandomId(8);
            ((BasicTask)task).thread.setName(newThreadName);
        }
        ((BasicTask)task).thread = null;
        synchronized (task) { task.notifyAll(); }

        for (ExecutionListener listener : listeners) {
            try {
                listener.onTaskDone(task);
            } catch (Exception e) {
                log.warn("Error notifying listener "+listener+" of task "+task+" done", e);
            }
        }
    }

    /** Returns {@link TaskPreprocessor} defined for tasks with the given tag, or null if none. */
    @Deprecated
    public TaskPreprocessor getTaskPreprocessorForTag(Object tag) { return preprocessorByTag.get(tag); }

    /** @see #setTaskPreprocessorForTag(Object, TaskPreprocessor) */
    @SuppressWarnings("deprecation")
    public void setTaskPreprocessorForTag(Object tag, Class<? extends TaskPreprocessor> preprocessor) {
        synchronized (preprocessorByTag) {
            TaskPreprocessor old = getTaskPreprocessorForTag(tag);
            if (old!=null) {
                if (preprocessor.isAssignableFrom(old.getClass())) {
                    /* already have such an instance */
                    return;
                }
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag "+tag+", has "+old+", setting new "+preprocessor+")");
            }
            try {
                setTaskPreprocessorForTag(tag, preprocessor.newInstance());
            } catch (InstantiationException e) {
                throw Throwables.propagate(e);
            } catch (IllegalAccessException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Defines a {@link TaskPreprocessor} to run on all subsequently submitted jobs with the given tag.
     *
     * Maximum of one allowed currently. Resubmissions of the same preprocessor (or preprocessor class)
     * allowed. If changing, you must call {@link #clearTaskPreprocessorForTag(Object)} between the two.
     *
     * @see #setTaskPreprocessorForTag(Object, Class)
     */
    @SuppressWarnings("deprecation")
    public void setTaskPreprocessorForTag(Object tag, TaskPreprocessor preprocessor) {
        synchronized (preprocessorByTag) {
            preprocessor.injectManager(this);
            preprocessor.injectTag(tag);

            Object old = preprocessorByTag.put(tag, preprocessor);
            if (old!=null && old!=preprocessor) {
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag "+tag+")");
            }
        }
    }

    public TaskScheduler getTaskSchedulerForTag(Object tag) {
        return schedulerByTag.get(tag);
    }
    
    /**
     * Forgets that any preprocessor was associated with a tag.
     *
     * @see #setTaskPreprocessorForTag(Object, TaskPreprocessor)
     * @see #setTaskPreprocessorForTag(Object, Class)
     */
    public boolean clearTaskPreprocessorForTag(Object tag) {
        synchronized (preprocessorByTag) {
            Object old = preprocessorByTag.remove(tag);
            return (old!=null);
        }
    }
    
    public void setTaskSchedulerForTag(Object tag, Class<? extends TaskScheduler> scheduler) {
        synchronized (schedulerByTag) {
            TaskScheduler old = getTaskSchedulerForTag(tag);
            if (old!=null) {
                if (scheduler.isAssignableFrom(old.getClass())) {
                    /* already have such an instance */
                    return;
                }
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskSchedulers on ExecutionManager tag (tag "+tag+", has "+old+", setting new "+scheduler+")");
            }
            try {
                TaskScheduler schedulerI = scheduler.newInstance();
                // allow scheduler to have a nice name, for logging etc
                if (schedulerI instanceof CanSetName) ((CanSetName)schedulerI).setName(""+tag);
                setTaskSchedulerForTag(tag, schedulerI);
            } catch (InstantiationException e) {
                throw Throwables.propagate(e);
            } catch (IllegalAccessException e) {
                throw Throwables.propagate(e);
            }
        }
    }
    
    /**
     * Defines a {@link TaskScheduler} to run on all subsequently submitted jobs with the given tag.
     *
     * Maximum of one allowed currently. Resubmissions of the same scheduler (or scheduler class)
     * allowed. If changing, you must call {@link #clearTaskSchedulerForTag(Object)} between the two.
     *
     * @see #setTaskSchedulerForTag(Object, Class)
     */
    public void setTaskSchedulerForTag(Object tag, TaskScheduler scheduler) {
        synchronized (schedulerByTag) {
            scheduler.injectExecutor(runner);

            Object old = schedulerByTag.put(tag, scheduler);
            if (old!=null && old!=scheduler) {
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskSchedulers on ExecutionManager tag (tag "+tag+")");
            }
        }
    }

    /**
     * Forgets that any scheduler was associated with a tag.
     *
     * @see #setTaskSchedulerForTag(Object, TaskScheduler)
     * @see #setTaskSchedulerForTag(Object, Class)
     */
    public boolean clearTaskSchedulerForTag(Object tag) {
        synchronized (schedulerByTag) {
            Object old = schedulerByTag.remove(tag);
            return (old!=null);
        }
    }
}
