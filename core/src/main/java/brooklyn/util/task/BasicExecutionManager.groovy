package brooklyn.util.task

import groovy.lang.Closure

import java.util.Collections
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import java.util.Set
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.management.ExecutionManager
import brooklyn.management.ExpirationPolicy
import brooklyn.management.Task
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.CaseFormat

/**
 * TODO javadoc
 */
public class BasicExecutionManager implements ExecutionManager {
    private static final Logger log = LoggerFactory.getLogger(BasicExecutionManager.class)

    /**
     * Renaming threads can really helps with debugging etc; however it's a massive performance hit.
     * We can run approx 2300 tasks per sec (submitted from a single thread) when using thread-renaming,
     * compared to almost 6500 when this is disabled.
     * 
     * Defaults to false if system property is not set.
     */
    private static final boolean RENAME_THREADS = Boolean.parseBoolean(System.getProperty("brooklyn.executionManager.renameThreads"))
    
    private static class PerThreadCurrentTaskHolder {
        public static final perThreadCurrentTask = new ThreadLocal<Task>()
    }

    public static ThreadLocal<Task> getPerThreadCurrentTask() {
        return PerThreadCurrentTaskHolder.perThreadCurrentTask
    }

    public static Task getCurrentTask() { return getPerThreadCurrentTask().get() }
    
    private ThreadFactory threadFactory = newThreadFactory();
    private ThreadFactory daemonThreadFactory = { runnable -> Thread t = threadFactory.newThread(runnable); t.setDaemon(true); t } as ThreadFactory
    
    private ExecutorService runner = 
		new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), daemonThreadFactory);
//      above is Executors.newCachedThreadPool(daemonThreadFactory)  but timeout of 1s rather than 60s for better shutdown!
        
	private ScheduledExecutorService delayedRunner = new ScheduledThreadPoolExecutor(1, daemonThreadFactory);
	
    // TODO Could have a set of all knownTasks; but instead we're having a separate set per tag,
    // so the same task could be listed multiple times if it has multiple tags...

    //access to the below is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
    //(but more testing is needed before we are sure it is thread-safe!)
    //synch blocks are as finely grained as possible for efficiency
    //Not using a CopyOnWriteArraySet for each, because profiling showed this being a massive perf bottleneck.
    private ConcurrentMap<Object,Set<Task>> tasksByTag = new ConcurrentHashMap()

    private ConcurrentMap<Object, TaskPreprocessor> preprocessorByTag = new ConcurrentHashMap()

    private ConcurrentMap<Object, TaskScheduler> schedulerByTag = new ConcurrentHashMap()
    
    private final AtomicLong totalTaskCount = new AtomicLong()
    
    private final AtomicInteger incompleteTaskCount = new AtomicInteger()
    
    private final AtomicInteger activeTaskCount = new AtomicInteger()
    
	/** for use by overriders to use custom thread factory */
	protected ThreadFactory newThreadFactory() {
		Executors.defaultThreadFactory();
	}
	
    public void shutdownNow() {
        runner.shutdownNow()
    }
    
    public long getTotalTasksSubmitted() {
        return totalTaskCount.get()
    }
    
    public long getNumIncompleteTasks() {
        return incompleteTaskCount.get()
    }
    
    public long getNumActiveTasks() {
        return activeTaskCount.get()
    }
    
    private Set<Task> getMutableTasksWithTag(Object tag) {
        tasksByTag.putIfAbsent(tag, Collections.synchronizedSet(new LinkedHashSet<Task>()))
        return tasksByTag.get(tag)
    }

    public Set<Task> getTasksWithTag(Object tag) {
        Set<Task> result = getMutableTasksWithTag(tag)
        synchronized (result) {
            return Collections.unmodifiableSet(new LinkedHashSet<Task>(result))
        }
    }
    
    public Set<Task> getTasksWithAnyTag(Iterable tags) {
        Set result = new LinkedHashSet<Task>()
        tags.each { tag -> result.addAll getTasksWithTag(tag) }
        result
    }

    public Set<Task> getTasksWithAllTags(Iterable tags) {
        //NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
        //by first looking for the least-used tag, getting those tasks, and then for each of those tasks
        //checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
        Set result = new LinkedHashSet<Task>()
        boolean first = true
        tags.each { tag -> 
            if (first) { 
                first = false
                result.addAll(getTasksWithTag(tag))
            } else {
                result.retainAll(getTasksWithTag(tag))
            }
        }
        result
    }

    public Set<Object> getTaskTags() { return tasksByTag.keySet() }

    public Task<?> submit(Map flags=[:], Runnable r) { submit flags, new BasicTask(flags, r) }

    public <T> Task<T> submit(Map flags=[:], Callable<T> c) { submit flags, new BasicTask<T>(flags, c) }

    public <T> Task<T> submit(Map flags=[:], Task<T> task) {
        synchronized (task) {
            if (task.result!=null) return task
            submitNewTask flags, task
        }
    }

	public <T> Task<T> scheduleWith(Map flags=[:], Task<T> task) {
		synchronized (task) {
			if (task.result!=null) return task
			submitNewTask flags, task
		}
	}

	protected Task submitNewTask(Map flags, ScheduledTask task) {
		task.submitTimeUtc = System.currentTimeMillis()
		if (!task.isDone()) {
			task.result = delayedRunner.schedule({
				if (task.startTimeUtc==-1) task.startTimeUtc = System.currentTimeMillis();
				def taskScheduled = task.newTask()
				taskScheduled.submittedByTask = task
				def oldJob = taskScheduled.job
				taskScheduled.job = {
					task.recentRun = taskScheduled
					Object result = oldJob.call();
					task.runCount++;
					if (task.period!=null) {
						task.delay = task.period
						submitNewTask flags, task
					}
					result;
				}
				task.nextRun = taskScheduled
				submit taskScheduled
			} as Callable,
			task.delay.toMilliseconds(), TimeUnit.MILLISECONDS)
		} else {
			task.endTimeUtc = System.currentTimeMillis();
		}
		task
	}

    protected <T> Task<T> submitNewTask(Map flags, Task<T> task) {
        totalTaskCount.incrementAndGet()
        
        beforeSubmit(flags, task)
        
        Closure job = {
            Object result = null
            String oldThreadName = Thread.currentThread().getName();
            try {
                if (RENAME_THREADS) {
                    String newThreadName = oldThreadName+"-"+task.getDisplayName()+"["+task.id[0..8]+"]"
                    Thread.currentThread().setName(newThreadName)
                }
                beforeStart(flags, task)
                if (!task.isCancelled()) {
                    result = task.job.call()
                } else throw new CancellationException()
            } catch(Throwable e) {
                result = e
            } finally {
                if (RENAME_THREADS) {
                    Thread.currentThread().setName(oldThreadName)
                }
                afterEnd(flags, task)
            }
            if (result instanceof Throwable) {
                log.warn "Error while running task $task (rethrowing): ${result.message}", (Throwable)result
                throw result
            }
            result
        }
        task.initExecutionManager(this)
        
        // If there's a scheduler then use that; otherwise execute it directly
        // 'as Callable' to prevent being treated as Runnable and returning a future that gives null
        Set<TaskScheduler> schedulers = []
        task.@tags.each {
            TaskScheduler scheduler = getTaskSchedulerForTag(it)
            if (scheduler) schedulers << scheduler
        }
        Future future
        if (schedulers) {
			if (schedulers.size()>1) log.warn "multiple schedulers detected, using only the first, for ${task}: "+schedulers
            future = schedulers.iterator().next().submit(job as Callable)
        } else {
            future = runner.submit(job as Callable)
        }

        task.initResult(future)
        task
    }

    protected void beforeSubmit(Map flags, Task<?> task) {
        incompleteTaskCount.incrementAndGet()
        
		Task currentTask = getCurrentTask();
        if (currentTask) task.submittedByTask = currentTask
        task.submitTimeUtc = System.currentTimeMillis()
        
        if (flags.tag) task.@tags.add flags.remove("tag")
        if (flags.tags) task.@tags.addAll flags.remove("tags")

        task.@tags.each { tag -> 
            getMutableTasksWithTag(tag) << task
        }
        
        List tagLinkedPreprocessors = []
        task.@tags.each {
            TaskPreprocessor p = getTaskPreprocessorForTag(it)
            if (p) tagLinkedPreprocessors << p
        }
        flags.tagLinkedPreprocessors = tagLinkedPreprocessors
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onSubmit(flags, task) }
    }

    protected void beforeStart(Map flags, Task<?> task) {
        activeTaskCount.incrementAndGet()
        
        //set thread _before_ start time, so we won't get a null thread when there is a start-time
        if (log.isTraceEnabled()) log.trace "$this beforeStart, task: $task"
        if (!task.isCancelled()) {
            task.thread = Thread.currentThread()
            if (RENAME_THREADS) {
                String newThreadName = "brooklyn-" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, task.displayName.replace(" ", "")) + "-" + task.id[0..7]
                task.thread.setName(newThreadName)
            }
            perThreadCurrentTask.set task
            task.startTimeUtc = System.currentTimeMillis()
        }
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onStart(flags, task) }
        ExecutionUtils.invoke flags.newTaskStartCallback, task
    }

    protected void afterEnd(Map flags, Task<?> task) {
        activeTaskCount.decrementAndGet()
        incompleteTaskCount.decrementAndGet()

        if (log.isTraceEnabled()) log.trace "$this afterEnd, task: $task"
        ExecutionUtils.invoke flags.newTaskEndCallback, task
        Collections.reverse(flags.tagLinkedPreprocessors)
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onEnd(flags, task) }

        perThreadCurrentTask.remove()
        task.endTimeUtc = System.currentTimeMillis()
        //clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
        if (RENAME_THREADS) {
            String newThreadName = "brooklyn-"+LanguageUtils.newUid()
            task.thread.setName(newThreadName)
        }
        task.thread = null
        synchronized (task) { task.notifyAll() }

        ExpirationPolicy expirationPolicy = flags.expirationPolicy ?: ExpirationPolicy.IMMEDIATE
        if (expirationPolicy == ExpirationPolicy.IMMEDIATE) {
            task.@tags.each { tag ->
                getMutableTasksWithTag(tag).remove(task)
            }
        }
    }

    /** Returns {@link TaskPreprocessor} defined for tasks with the given tag, or null if none. */
    public TaskPreprocessor getTaskPreprocessorForTag(Object tag) { return preprocessorByTag.get(tag) }

    /** @see #setTaskPreprocessorForTag(Object, TaskPreprocessor) */
    public void setTaskPreprocessorForTag(Object tag, Class<? extends TaskPreprocessor> preprocessor) {
        synchronized (preprocessorByTag) {
            TaskPreprocessor old = getTaskPreprocessorForTag(tag)
            if (old!=null) {
                if (preprocessor.isAssignableFrom(old.getClass())) {
                    /* already have such an instance */
                    return
                }
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag $tag, has $old, setting new $preprocessor)")
            }
            setTaskPreprocessorForTag(tag, preprocessor.newInstance())
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
    public void setTaskPreprocessorForTag(Object tag, TaskPreprocessor preprocessor) {
        synchronized (preprocessorByTag) {
            preprocessor.injectManager(this)
            preprocessor.injectTag(tag)

            def old = preprocessorByTag.put(tag, preprocessor)
            if (old!=null && old!=preprocessor) {
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag $tag)")
            }
        }
    }

    public TaskScheduler getTaskSchedulerForTag(Object tag) {
        return schedulerByTag.get(tag)
    }
    
    /**
     * Forgets that any preprocessor was associated with a tag.
     *
     * @see #setTaskPreprocessorForTag(Object, TaskPreprocessor)
     * @see #setTaskPreprocessorForTag(Object, Class)
     */
    public boolean clearTaskPreprocessorForTag(Object tag) {
        synchronized (preprocessorByTag) {
            def old = preprocessorByTag.remove(tag)
            return (old!=null)
        }
    }
    
    public void setTaskSchedulerForTag(Object tag, Class<? extends TaskScheduler> scheduler) {
        synchronized (schedulerByTag) {
            TaskScheduler old = getTaskSchedulerForTag(tag)
            if (old!=null) {
                if (scheduler.isAssignableFrom(old.getClass())) {
                    /* already have such an instance */
                    return
                }
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskSchedulers on ExecutionManager tag (tag $tag, has $old, setting new $scheduler)")
            }
            setTaskSchedulerForTag(tag, scheduler.newInstance())
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
            scheduler.injectExecutor(runner)

            def old = schedulerByTag.put(tag, scheduler)
            if (old!=null && old!=scheduler) {
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskSchedulers on ExecutionManager tag (tag $tag)")
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
            def old = schedulerByTag.remove(tag)
            return (old!=null)
        }
    }
	
}
