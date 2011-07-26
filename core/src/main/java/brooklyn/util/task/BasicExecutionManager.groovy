package brooklyn.util.task

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.management.ExecutionManager
import brooklyn.management.Task

/**
 * TODO javadoc
 */
public class BasicExecutionManager implements ExecutionManager {
    private static final Logger log = LoggerFactory.getLogger(BasicExecutionManager.class)

    private static class PerThreadCurrentTaskHolder {
        public static final perThreadCurrentTask = new ThreadLocal<Task>()
    }

    public static ThreadLocal<Task> getPerThreadCurrentTask() {
        return PerThreadCurrentTaskHolder.perThreadCurrentTask
    }

    public static Task getCurrentTask() { return getPerThreadCurrentTask().get() }
    
    private ExecutorService runner = Executors.newCachedThreadPool()
    
    private Set<Task> knownTasks = new CopyOnWriteArraySet<Task>()

    private Map<Object,Set<Task>> tasksByTag = new LinkedHashMap()
    //access to the above is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
    //(but more testing is needed before we are sure it is thread-safe!)
    //synch blocks are as finely grained as possible for efficiency

    private ConcurrentMap<Object, TaskPreprocessor> preprocessorByTag = new ConcurrentHashMap()

    private ConcurrentMap<Object, TaskScheduler> schedulerByTag = new ConcurrentHashMap()
    
    public void shutdownNow() {
        runner.shutdownNow()
    }
    
    public Set<Task> getTasksWithTag(Object tag) {
        Set<Task> tasksWithTag
        synchronized (tasksByTag) {
            tasksWithTag = tasksByTag.get(tag)
	        if (tasksWithTag == null) return Collections.emptySet()
	        return new LinkedHashSet(tasksWithTag)
        }
    }

    public Set<Task> getTasksWithAnyTag(Iterable tags) {
        Set result = []
        tags.each { tag -> result.addAll getTasksWithTag(tag) }
        result
    }

    public Set<Task> getTasksWithAllTags(Iterable tags) {
        //NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
        //by first looking for the least-used tag, getting those tasks, and then for each of those tasks
        //checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
        Set result = null
        tags.each {
            tag ->
            if (result==null) result = getTasksWithTag(tag)
            else {
                result.retainAll getTasksWithTag(tag)
                if (!result) return result  //abort if we are already empty
            }
        }
        result
    }

    public Set<Object> getTaskTags() { synchronized (tasksByTag) { return new LinkedHashSet(tasksByTag.keySet()) }}

    public Set<Task> getAllTasks() { synchronized (knownTasks) { return new LinkedHashSet(knownTasks) }}

    public Task<?> submit(Map flags=[:], Runnable r) { submit flags, new BasicTask(flags, r) }

    public <T> Task<T> submit(Map flags=[:], Callable<T> c) { submit flags, new BasicTask<T>(flags, c) }

    public <T> Task<T> submit(Map flags=[:], Task<T> task) {
        synchronized (task) {
            if (task.result!=null) return task
            submitNewTask flags, task
        }
    }

    protected <T> Task<T> submitNewTask(Map flags, Task<T> task) {
        beforeSubmit(flags, task)
        
        Closure job = {
            Object result = null
            String oldThreadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(oldThreadName+"-"+task.getDisplayName()+"["+task.id[0..8]+"]")
                beforeStart(flags, task)
                if (!task.isCancelled()) {
                    result = task.job.call()
                } else throw new CancellationException()
            } catch(Exception e) {
                result = e
            } finally {
                Thread.currentThread().setName(oldThreadName)
                afterEnd(flags, task)
            }
            if (result instanceof Exception) {
                log.warn "Error while running task {}", result.message
                throw result
            }
            result
        }
        task.initExecutionManager(this)
        
        // If there's a scheduler then use that; otherwise execute it directly
        // 'as Callable' to prevent being treated as Runnable and returning a future that gives null
        List<TaskScheduler> schedulers = []
        task.@tags.each {
            TaskScheduler scheduler = getTaskSchedulerForTag(it)
            if (scheduler) schedulers << scheduler
        }
        Future future
        if (schedulers) {
            future = schedulers.get(0).submit(job as Callable)
        } else {
            future = runner.submit(job as Callable)
        }

        task.initResult(future)
        task
    }

    protected void beforeSubmit(Map flags, Task<?> task) {
        task.submittedByTask = getCurrentTask()
        task.submitTimeUtc = System.currentTimeMillis()
        synchronized (knownTasks) {
            knownTasks << task
        }
        if (flags.tag) task.@tags.add flags.remove("tag")
        if (flags.tags) task.@tags.addAll flags.remove("tags")

        List tagBuckets = []
        synchronized (tasksByTag) {
            task.@tags.each { tag ->
                Set tagBucket = tasksByTag.get tag
                if (tagBucket==null) {
                    tagBucket = new LinkedHashSet()
                    tasksByTag.put tag, tagBucket
                }
                tagBuckets.add tagBucket
            }
        }
        tagBuckets.each { bucket ->
            synchronized (bucket) {
                bucket << task
            }
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
        //set thread _before_ start time, so we won't get a null thread when there is a start-time
        log.trace "$this beforeStart, task: $task"
        if (!task.isCancelled()) {
            task.thread = Thread.currentThread()
            perThreadCurrentTask.set task
            task.startTimeUtc = System.currentTimeMillis()
        }
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onStart(flags, task) }
        ExecutionUtils.invoke flags.newTaskStartCallback, task
    }

    protected void afterEnd(Map flags, Task<?> task) {
        log.trace "$this afterEnd, task: $task"
        ExecutionUtils.invoke flags.newTaskEndCallback, task
        Collections.reverse(flags.tagLinkedPreprocessors)
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onEnd(flags, task) }

        perThreadCurrentTask.remove()
        task.endTimeUtc = System.currentTimeMillis()
        //clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
        task.thread = null
        synchronized (task) { task.notifyAll() }
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
