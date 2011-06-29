package brooklyn.util.task;

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import brooklyn.management.ExecutionManager
import brooklyn.management.Task


public class BasicExecutionManager implements ExecutionManager {
    
    private static class PerThreadCurrentTaskHolder {
        public static final perThreadCurrentTask = new ThreadLocal<Task>();
    }
    public static ThreadLocal<Task> getPerThreadCurrentTask() {
        return PerThreadCurrentTaskHolder.perThreadCurrentTask;
    }
    
    public static Task getCurrentTask() { return getPerThreadCurrentTask().get() }
    
    private ExecutorService runner = Executors.newCachedThreadPool() 
    
    private Set<Task> knownTasks = new LinkedHashSet()
    private Map<Object,Set<Task>> tasksByTag = new LinkedHashMap()
    //access to the above is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
    //(but more testing is needed before we are sure it is thread-safe!)
    //synch blocks are as finely grained as possible for efficiency

    private Map<Object,TaskPreprocessor> preprocessorByTag = new ConcurrentHashMap();
    
    public Set<Task> getTasksWithTag(Object tag) {
        Set<Task> tasksWithTag;
        synchronized (tasksByTag) {
            tasksWithTag = tasksByTag.get(tag)
        }
        if (tasksWithTag==null) return Collections.emptySet()
        synchronized (tasksWithTag) {
            return new LinkedHashSet(tasksWithTag)
        } 
    }
    public Set<Task> getTasksWithAnyTag(Iterable tags) {
        Set result = []
        tags.each { tag -> result.addAll( getTasksWithTag(tag) ) }
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
    
    public Task submit(Map flags=[:], Runnable r) { submit flags, new BasicTask(r) }
    public Task submit(Map flags=[:], Callable r) { submit flags, new BasicTask(r) }
    public Task submit(Map flags=[:], Task task) {
        if (task.result!=null) return task
        synchronized (task) {
            if (task.result!=null) return task
            submitNewTask flags, task
        }
    }

    protected Task submitNewTask(Map flags, Task task) {
        beforeSubmit(flags, task)
        Closure job = { 
            Object result = null
            try { 
                beforeStart(flags, task);
                if (!task.isCancelled())
                    result = task.job.call()
                else 
                    throw new CancellationException()
            } finally { 
                afterEnd(flags, task) 
            }
            result
        }
        task.initExecutionManager(this)
        // 'as Callable' to prevent being treated as Runnable and returning a future that gives null
        task.initResult(runner.submit(job as Callable))
        task
    }

    protected void beforeSubmit(Map flags, Task task) {
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
            TaskPreprocessor p = getTaskPreprocessorForTag(it);
//            println "submit: task $task, tag $it, preprocessor $p"
            if (p) tagLinkedPreprocessors << p
        }
        flags.tagLinkedPreprocessors = tagLinkedPreprocessors
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onSubmit(flags, task) }
    }    
    protected void beforeStart(Map flags, Task task) {
        //set thread _before_ start time, so we won't get a null thread when there is a start-time
        if (!task.isCancelled()) {
            task.thread = Thread.currentThread()
            perThreadCurrentTask.set task
            task.startTimeUtc = System.currentTimeMillis()
        }
//        println "set thread for $task as "+task.thread
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onStart(flags, task) }
        ExecutionUtils.invoke flags.newTaskStartCallback, task
    }

    protected void afterEnd(Map flags, Task task) {
        ExecutionUtils.invoke flags.newTaskEndCallback, task
        Collections.reverse(flags.tagLinkedPreprocessors)
        flags.tagLinkedPreprocessors.each { TaskPreprocessor t -> t.onEnd(flags, task) }
        
        perThreadCurrentTask.remove()
        task.endTimeUtc = System.currentTimeMillis()
        //clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
//        println "clearing thread for $task as "+task.thread
        task.thread = null;
        synchronized (task) { task.notifyAll() }
    }

    /** returns {@link TaskPreprocessor} defined for tasks with the given tag, or null if none */
    public TaskPreprocessor getTaskPreprocessorForTag(Object tag) { return preprocessorByTag.get(tag) }
    /** @see #setTaskPreprocessorForTag(Object, TaskPreprocessor) */
    public void setTaskPreprocessorForTag(Object tag, Class<? extends TaskPreprocessor> preprocessor) {
        synchronized (preprocessorByTag) {
            def old = getTaskPreprocessorForTag(tag)
            if (old!=null) {
                if (preprocessor.isAssignableFrom(old)) {
                    /* already have such an instance */ 
                    return;
                }
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag $tag, has $old, setting new $preprocessor)");
            }
            setTaskPreprocessorForTag(tag, preprocessor.newInstance())
        }
    }
    /** defines a {@link TaskPreprocessor} to run on all subsequently submitted jobs with the given tag;
     * max 1 allowed currently; resubmissions of same preprocessor (or preprocessor class) allowed; 
     * if changing, you must call {@link #clearTaskPreprocessorForTag(Object)} between the two */
    public void setTaskPreprocessorForTag(Object tag, TaskPreprocessor preprocessor) {
        synchronized (preprocessorByTag) {
            preprocessor.injectManager(this)
            preprocessor.injectTag(tag)

            def old = preprocessorByTag.put(tag, preprocessor);
            if (old!=null && old!=preprocessor) {
                //might support multiple in future...
                throw new IllegalStateException("Not allowed to set multiple TaskProcessors on ExecutionManager tag (tag $tag)");
            }
        }
    }
    /** forgets that any preprocessor was associated with a tag; @see #setTaskPreprocessorForTag */ 
    public boolean clearTaskPreprocessorForTag(Object tag) {
        synchronized (preprocessorByTag) {
            def old = preprocessorByTag.clear(tag)
            return (old!=null)
        }
    }

}
