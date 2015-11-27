/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.core.task;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Manages the execution of atomic tasks and scheduled (recurring) tasks,
 * including setting tags and invoking callbacks.
 */
public class BasicExecutionManager implements ExecutionManager {
    private static final Logger log = LoggerFactory.getLogger(BasicExecutionManager.class);

    private static final boolean RENAME_THREADS = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_RENAME_THREADS);
    
    private static class PerThreadCurrentTaskHolder {
        public static final ThreadLocal<Task<?>> perThreadCurrentTask = new ThreadLocal<Task<?>>();
    }

    public static ThreadLocal<Task<?>> getPerThreadCurrentTask() {
        return PerThreadCurrentTaskHolder.perThreadCurrentTask;
    }

    private final ThreadFactory threadFactory;
    
    private final ThreadFactory daemonThreadFactory;
    
    private final ExecutorService runner;
        
    private final ScheduledExecutorService delayedRunner;
    
    // TODO Could have a set of all knownTasks; but instead we're having a separate set per tag,
    // so the same task could be listed multiple times if it has multiple tags...

    //access to this field AND to members in this field is synchronized, 
    //to allow us to preserve order while guaranteeing thread-safe
    //(but more testing is needed before we are completely sure it is thread-safe!)
    //synch blocks are as finely grained as possible for efficiency;
    //NB CopyOnWriteArraySet is a perf bottleneck, and the simple map makes it easier to remove when a tag is empty
    private Map<Object,Set<Task<?>>> tasksByTag = new HashMap<Object,Set<Task<?>>>();
    
    private ConcurrentMap<String,Task<?>> tasksById = new ConcurrentHashMap<String,Task<?>>();

    private ConcurrentMap<Object, TaskScheduler> schedulerByTag = new ConcurrentHashMap<Object, TaskScheduler>();

    /** count of all tasks submitted, including finished */
    private final AtomicLong totalTaskCount = new AtomicLong();
    
    /** tasks submitted but not yet done (or in cases of interruption/cancelled not yet GC'd) */
    private Map<String,String> incompleteTaskIds = new ConcurrentHashMap<String,String>();
    
    /** tasks started but not yet finished */
    private final AtomicInteger activeTaskCount = new AtomicInteger();
    
    private final List<ExecutionListener> listeners = new CopyOnWriteArrayList<ExecutionListener>();
    
    private final static ThreadLocal<String> threadOriginalName = new ThreadLocal<String>() {
        protected String initialValue() {
            // should not happen, as only access is in _afterEnd with a check that _beforeStart was invoked 
            log.warn("No original name recorded for thread "+Thread.currentThread().getName()+"; task "+Tasks.current());
            return "brooklyn-thread-pool-"+Identifiers.makeRandomId(8);
        }
    };
    
    public BasicExecutionManager(String contextid) {
        threadFactory = newThreadFactory(contextid);
        daemonThreadFactory = new ThreadFactoryBuilder()
                .setThreadFactory(threadFactory)
                .setDaemon(true)
                .build();
                
        // use Executors.newCachedThreadPool(daemonThreadFactory), but timeout of 1s rather than 60s for better shutdown!
        runner = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), 
                daemonThreadFactory);
            
        delayedRunner = new ScheduledThreadPoolExecutor(1, daemonThreadFactory);
    }
    
    private final static class UncaughtExceptionHandlerImplementation implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("Uncaught exception in thread "+t.getName(), e);
        }
    }
    
    /** 
     * For use by overriders to use custom thread factory.
     * But be extremely careful: called by constructor, so before sub-class' constructor will
     * have been invoked!
     */
    protected ThreadFactory newThreadFactory(String contextid) {
        return new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-execmanager-"+contextid+"-%d")
                .setUncaughtExceptionHandler(new UncaughtExceptionHandlerImplementation())
                .build();
    }
    
    public void shutdownNow() {
        runner.shutdownNow();
        delayedRunner.shutdownNow();
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
     * Useful, for example, if an entity is being expunged so that we don't keep holding
     * a reference to it as a tag.
     */
    public void deleteTag(Object tag) {
        Set<Task<?>> tasks;
        synchronized (tasksByTag) {
            tasks = tasksByTag.remove(tag);
        }
        if (tasks != null) {
            for (Task<?> task : tasks) {
                deleteTask(task);
            }
        }
    }

    public void deleteTask(Task<?> task) {
        boolean removed = deleteTaskNonRecursive(task);
        if (!removed) return;
        
        if (task instanceof HasTaskChildren) {
            List<Task<?>> children = ImmutableList.copyOf(((HasTaskChildren)task).getChildren());
            for (Task<?> child : children) {
                deleteTask(child);
            }
        }
    }

    protected boolean deleteTaskNonRecursive(Task<?> task) {
        Set<?> tags = checkNotNull(task, "task").getTags();
        for (Object tag : tags) {
            synchronized (tasksByTag) {
                Set<Task<?>> tasks = tasksWithTagLiveOrNull(tag);
                if (tasks != null) {
                    tasks.remove(task);
                    if (tasks.isEmpty()) {
                        tasksByTag.remove(tag);
                    }
                }
            }
        }
        Task<?> removed = tasksById.remove(task.getId());
        incompleteTaskIds.remove(task.getId());
        if (removed!=null && removed.isSubmitted() && !removed.isDone()) {
            log.warn("Deleting submitted task before completion: "+removed+"; this task will continue to run in the background outwith "+this+", but perhaps it should have been cancelled?");
        }
        return removed != null;
    }

    public boolean isShutdown() {
        return runner.isShutdown();
    }
    
    /** count of all tasks submitted */
    public long getTotalTasksSubmitted() {
        return totalTaskCount.get();
    }
    
    /** count of tasks submitted but not ended */
    public long getNumIncompleteTasks() {
        return incompleteTaskIds.size();
    }
    
    /** count of tasks started but not ended */
    public long getNumActiveTasks() {
        return activeTaskCount.get();
    }

    /** count of tasks kept in memory, often including ended tasks */
    public long getNumInMemoryTasks() {
        return tasksById.size();
    }

    private Set<Task<?>> tasksWithTagCreating(Object tag) {
        Preconditions.checkNotNull(tag);
        synchronized (tasksByTag) {
            Set<Task<?>> result = tasksWithTagLiveOrNull(tag);
            if (result==null) {
                result = Collections.synchronizedSet(new LinkedHashSet<Task<?>>());
                tasksByTag.put(tag, result);
            }
            return result;
        }
    }

    /** exposes live view, for internal use only */
    @Beta
    public Set<Task<?>> tasksWithTagLiveOrNull(Object tag) {
        synchronized (tasksByTag) {
            return tasksByTag.get(tag);
        }
    }

    @Override
    public Task<?> getTask(String id) {
        return tasksById.get(id);
    }
    
    /** not on interface because potentially expensive */
    public List<Task<?>> getAllTasks() {
        // not sure if synching makes any difference; have not observed CME's yet
        // (and so far this is only called when a CME was caught on a previous operation)
        synchronized (tasksById) {
            return MutableList.copyOf(tasksById.values());
        }
    }
    
    @Override
    public Set<Task<?>> getTasksWithTag(Object tag) {
        Set<Task<?>> result = tasksWithTagLiveOrNull(tag);
        if (result==null) return Collections.emptySet();
        synchronized (result) {
            return (Set<Task<?>>)Collections.unmodifiableSet(new LinkedHashSet<Task<?>>(result));
        }
    }
    
    @Override
    public Set<Task<?>> getTasksWithAnyTag(Iterable<?> tags) {
        Set<Task<?>> result = new LinkedHashSet<Task<?>>();
        Iterator<?> ti = tags.iterator();
        while (ti.hasNext()) {
            Set<Task<?>> tasksForTag = tasksWithTagLiveOrNull(ti.next());
            if (tasksForTag!=null) {
                synchronized (tasksForTag) {
                    result.addAll(tasksForTag);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** only works with at least one tag; returns empty if no tags */
    @Override
    public Set<Task<?>> getTasksWithAllTags(Iterable<?> tags) {
        //NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
        //by first looking for the least-used tag, getting those tasks, and then for each of those tasks
        //checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
        Set<Task<?>> result = new LinkedHashSet<Task<?>>();
        boolean first = true;
        Iterator<?> ti = tags.iterator();
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

    /** live view of all tasks, for internal use only */
    @Beta
    public Collection<Task<?>> allTasksLive() { return tasksById.values(); }
    
    public Set<Object> getTaskTags() { 
        synchronized (tasksByTag) {
            return Collections.unmodifiableSet(Sets.newLinkedHashSet(tasksByTag.keySet())); 
        }
    }

    public Task<?> submit(Runnable r) { return submit(new LinkedHashMap<Object,Object>(1), r); }
    public Task<?> submit(Map<?,?> flags, Runnable r) { return submit(flags, new BasicTask<Void>(flags, r)); }

    public <T> Task<T> submit(Callable<T> c) { return submit(new LinkedHashMap<Object,Object>(1), c); }
    public <T> Task<T> submit(Map<?,?> flags, Callable<T> c) { return submit(flags, new BasicTask<T>(flags, c)); }

    public <T> Task<T> submit(TaskAdaptable<T> t) { return submit(new LinkedHashMap<Object,Object>(1), t); }
    public <T> Task<T> submit(Map<?,?> flags, TaskAdaptable<T> task) {
        if (!(task instanceof Task))
            task = task.asTask();
        synchronized (task) {
            if (((TaskInternal<?>)task).getInternalFuture()!=null) return (Task<T>)task;
            return submitNewTask(flags, (Task<T>) task);
        }
    }

    public <T> Task<T> scheduleWith(Task<T> task) { return scheduleWith(Collections.emptyMap(), task); }
    public <T> Task<T> scheduleWith(Map<?,?> flags, Task<T> task) {
        synchronized (task) {
            if (((TaskInternal<?>)task).getInternalFuture()!=null) return task;
            return submitNewTask(flags, task);
        }
    }

    protected Task<?> submitNewScheduledTask(final Map<?,?> flags, final ScheduledTask task) {
        tasksById.put(task.getId(), task);
        totalTaskCount.incrementAndGet();
        
        beforeSubmitScheduledTaskAllIterations(flags, task);
        
        return submitSubsequentScheduledTask(flags, task);
    }
    
    @SuppressWarnings("unchecked")
    protected Task<?> submitSubsequentScheduledTask(final Map<?,?> flags, final ScheduledTask task) {
        if (!task.isDone()) {
            task.internalFuture = delayedRunner.schedule(new ScheduledTaskCallable(task, flags),
                task.delay.toNanoseconds(), TimeUnit.NANOSECONDS);
        } else {
            afterEndScheduledTaskAllIterations(flags, task);
        }
        return task;
    }

    protected class ScheduledTaskCallable implements Callable<Object> {
        public ScheduledTask task;
        public Map<?,?> flags;

        public ScheduledTaskCallable(ScheduledTask task, Map<?, ?> flags) {
            this.task = task;
            this.flags = flags;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Object call() {
            if (task.startTimeUtc==-1) task.startTimeUtc = System.currentTimeMillis();
            TaskInternal<?> taskScheduled = null;
            try {
                beforeStartScheduledTaskSubmissionIteration(flags, task);
                taskScheduled = (TaskInternal<?>) task.newTask();
                taskScheduled.setSubmittedByTask(task);
                final Callable<?> oldJob = taskScheduled.getJob();
                final TaskInternal<?> taskScheduledF = taskScheduled;
                taskScheduled.setJob(new Callable() { public Object call() {
                    boolean shouldResubmit = true;
                    task.recentRun = taskScheduledF;
                    try {
                        synchronized (task) {
                            task.notifyAll();
                        }
                        Object result;
                        try {
                            result = oldJob.call();
                            task.lastThrownType = null;
                        } catch (Exception e) {
                            shouldResubmit = shouldResubmitOnException(oldJob, e);
                            throw Exceptions.propagate(e);
                        }
                        return result;
                    } finally {
                        // do in finally block in case we were interrupted
                        if (shouldResubmit) {
                            resubmit();
                        } else {
                            afterEndScheduledTaskAllIterations(flags, task);
                        }
                    }
                }});
                task.nextRun = taskScheduled;
                BasicExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
                if (ec!=null) return ec.submit(taskScheduled);
                else return submit(taskScheduled);
            } finally {
                afterEndScheduledTaskSubmissionIteration(flags, task, taskScheduled);
            }
        }

        private void resubmit() {
            task.runCount++;
            if (task.period!=null && !task.isCancelled()) {
                task.delay = task.period;
                submitSubsequentScheduledTask(flags, task);
            }
        }

        private boolean shouldResubmitOnException(Callable<?> oldJob, Exception e) {
            String message = "Error executing " + oldJob + " (scheduled job of " + task + " - " + task.getDescription() + ")";
            if (Tasks.isInterrupted()) {
                log.debug(message + "; cancelling scheduled execution: " + e);
                return false;
            } else if (task.cancelOnException) {
                log.warn(message + "; cancelling scheduled execution.", e);
                return false;
            } else {
                message += "; resubmitting task and throwing: " + e;
                if (!e.getClass().equals(task.lastThrownType)) {
                    task.lastThrownType = e.getClass();
                    message += " (logging subsequent exceptions at trace)";
                    log.debug(message);
                } else {
                    message += " (repeat exception)";
                    log.trace(message);
                }
                return true;
            }
        }

        @Override
        public String toString() {
            return "ScheduledTaskCallable["+task+","+flags+"]";
        }
    }

    private final class SubmissionCallable<T> implements Callable<T> {
        private final Map<?, ?> flags;
        private final Task<T> task;

        private SubmissionCallable(Map<?, ?> flags, Task<T> task) {
            this.flags = flags;
            this.task = task;
        }

        public T call() {
            try {
                T result = null;
                Throwable error = null;
                String oldThreadName = Thread.currentThread().getName();
                try {
                    if (RENAME_THREADS) {
                        String newThreadName = oldThreadName+"-"+task.getDisplayName()+
                            "["+task.getId().substring(0, 8)+"]";
                        Thread.currentThread().setName(newThreadName);
                    }
                    beforeStartAtomicTask(flags, task);
                    if (!task.isCancelled()) {
                        result = ((TaskInternal<T>)task).getJob().call();
                    } else throw new CancellationException();
                } catch(Throwable e) {
                    error = e;
                } finally {
                    if (RENAME_THREADS) {
                        Thread.currentThread().setName(oldThreadName);
                    }
                    afterEndAtomicTask(flags, task);
                }
                if (error!=null) {
                    /* we throw, after logging debug.
                     * the throw means the error is available for task submitters to monitor.
                     * however it is possible no one is monitoring it, in which case we will have debug logging only for errors.
                     * (the alternative, of warn-level logging in lots of places where we don't want it, seems worse!) 
                     */
                    if (log.isDebugEnabled()) {
                        // debug only here, because most submitters will handle failures
                        log.debug("Exception running task "+task+" (rethrowing): "+error.getMessage(), error);
                        if (log.isTraceEnabled())
                            log.trace("Trace for exception running task "+task+" (rethrowing): "+error.getMessage(), error);
                    }
                    throw Exceptions.propagate(error);
                }
                return result;
            } finally {
                ((TaskInternal<?>)task).runListeners();
            }
        }

        @Override
        public String toString() {
            return "BEM.call("+task+","+flags+")";
        }
    }

    private final static class ListenableForwardingFutureForTask<T> extends ListenableForwardingFuture<T> {
        private final Task<T> task;

        private ListenableForwardingFutureForTask(Future<T> delegate, ExecutionList list, Task<T> task) {
            super(delegate, list);
            this.task = task;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean result = false;
            if (!task.isCancelled()) result |= task.cancel(mayInterruptIfRunning);
            result |= super.cancel(mayInterruptIfRunning);
            ((TaskInternal<?>)task).runListeners();
            return result;
        }
    }

    private final class SubmissionListenerToCallOtherListeners<T> implements Runnable {
        private final Task<T> task;

        private SubmissionListenerToCallOtherListeners(Task<T> task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                ((TaskInternal<?>)task).runListeners();
            } catch (Exception e) {
                log.warn("Error running task listeners for task "+task+" done", e);
            }
            
            for (ExecutionListener listener : listeners) {
                try {
                    listener.onTaskDone(task);
                } catch (Exception e) {
                    log.warn("Error running execution listener "+listener+" of task "+task+" done", e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> Task<T> submitNewTask(final Map<?,?> flags, final Task<T> task) {
        if (log.isTraceEnabled()) log.trace("Submitting task {} ({}), with flags {}, and tags {}, job {}", 
                new Object[] {task.getId(), task, flags, task.getTags(), 
                (task instanceof TaskInternal ? ((TaskInternal<T>)task).getJob() : "<unavailable>")});
        
        if (task instanceof ScheduledTask)
            return (Task<T>) submitNewScheduledTask(flags, (ScheduledTask)task);
        
        tasksById.put(task.getId(), task);
        totalTaskCount.incrementAndGet();
        
        beforeSubmitAtomicTask(flags, task);
        
        if (((TaskInternal<T>)task).getJob() == null) 
            throw new NullPointerException("Task "+task+" submitted with with null job: job must be supplied.");
        
        Callable<T> job = new SubmissionCallable<T>(flags, task);
        
        // If there's a scheduler then use that; otherwise execute it directly
        Set<TaskScheduler> schedulers = null;
        for (Object tago: task.getTags()) {
            TaskScheduler scheduler = getTaskSchedulerForTag(tago);
            if (scheduler!=null) {
                if (schedulers==null) schedulers = new LinkedHashSet<TaskScheduler>(2);
                schedulers.add(scheduler);
            }
        }
        Future<T> future;
        if (schedulers!=null && !schedulers.isEmpty()) {
            if (schedulers.size()>1) log.warn("multiple schedulers detected, using only the first, for "+task+": "+schedulers);
            future = schedulers.iterator().next().submit(job);
        } else {
            future = runner.submit(job);
        }
        // on completion, listeners get triggered above; here, below we ensure they get triggered on cancel
        // (and we make sure the same ExecutionList is used in the future as in the task)
        ListenableFuture<T> listenableFuture = new ListenableForwardingFutureForTask<T>(future, ((TaskInternal<T>)task).getListeners(), task);
        // doesn't matter whether the listener is added to the listenableFuture or the task,
        // except that for the task we can more easily wrap it so that it only logs debug if the executor is shutdown
        // (avoid a bunch of ugly warnings in tests which start and stop things a lot!)
        // [probably even nicer to run this in the same thread, it doesn't do much; but that is messier to implement]
        ((TaskInternal<T>)task).addListener(new SubmissionListenerToCallOtherListeners<T>(task), runner);
        
        ((TaskInternal<T>)task).initInternalFuture(listenableFuture);
        
        return task;
    }
    
    protected void beforeSubmitScheduledTaskAllIterations(Map<?,?> flags, Task<?> task) {
        internalBeforeSubmit(flags, task);
    }
    protected void beforeSubmitAtomicTask(Map<?,?> flags, Task<?> task) {
        internalBeforeSubmit(flags, task);
    }
    /** invoked when a task is submitted */
    protected void internalBeforeSubmit(Map<?,?> flags, Task<?> task) {
        incompleteTaskIds.put(task.getId(), task.getId());
        
        Task<?> currentTask = Tasks.current();
        if (currentTask!=null) ((TaskInternal<?>)task).setSubmittedByTask(currentTask);
        ((TaskInternal<?>)task).setSubmitTimeUtc(System.currentTimeMillis());
        
        if (flags.get("tag")!=null) ((TaskInternal<?>)task).getMutableTags().add(flags.remove("tag"));
        if (flags.get("tags")!=null) ((TaskInternal<?>)task).getMutableTags().addAll((Collection<?>)flags.remove("tags"));

        for (Object tag: ((TaskInternal<?>)task).getTags()) {
            tasksWithTagCreating(tag).add(task);
        }
    }

    protected void beforeStartScheduledTaskSubmissionIteration(Map<?,?> flags, Task<?> task) {
        internalBeforeStart(flags, task);
    }
    protected void beforeStartAtomicTask(Map<?,?> flags, Task<?> task) {
        internalBeforeStart(flags, task);
    }
    
    /** invoked in a task's thread when a task is starting to run (may be some time after submitted), 
     * but before doing any of the task's work, so that we can update bookkeeping and notify callbacks */
    protected void internalBeforeStart(Map<?,?> flags, Task<?> task) {
        activeTaskCount.incrementAndGet();
        
        //set thread _before_ start time, so we won't get a null thread when there is a start-time
        if (log.isTraceEnabled()) log.trace(""+this+" beforeStart, task: "+task);
        if (!task.isCancelled()) {
            Thread thread = Thread.currentThread();
            ((TaskInternal<?>)task).setThread(thread);
            if (RENAME_THREADS) {
                threadOriginalName.set(thread.getName());
                String newThreadName = "brooklyn-" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, task.getDisplayName().replace(" ", "")) + "-" + task.getId().substring(0, 8);
                thread.setName(newThreadName);
            }
            PerThreadCurrentTaskHolder.perThreadCurrentTask.set(task);
            ((TaskInternal<?>)task).setStartTimeUtc(System.currentTimeMillis());
        }
        ExecutionUtils.invoke(flags.get("newTaskStartCallback"), task);
    }

    /** normally (if not interrupted) called once for each call to {@link #beforeSubmitScheduledTaskAllIterations(Map, Task)} */
    protected void afterEndScheduledTaskAllIterations(Map<?,?> flags, Task<?> task) {
        internalAfterEnd(flags, task, false, true);
    }
    /** called once for each call to {@link #beforeStartScheduledTaskSubmissionIteration(Map, Task)},
     * with a per-iteration task generated by the surrounding scheduled task */
    protected void afterEndScheduledTaskSubmissionIteration(Map<?,?> flags, Task<?> scheduledTask, Task<?> taskIteration) {
        internalAfterEnd(flags, scheduledTask, true, false);
    }
    /** called once for each task on which {@link #beforeStartAtomicTask(Map, Task)} is invoked,
     * and normally (if not interrupted prior to start) 
     * called once for each task on which {@link #beforeSubmitAtomicTask(Map, Task)} */
    protected void afterEndAtomicTask(Map<?,?> flags, Task<?> task) {
        internalAfterEnd(flags, task, true, true);
    }
    /** normally (if not interrupted) called once for each call to {@link #internalBeforeSubmit(Map, Task)},
     * and, for atomic tasks and scheduled-task submission iterations where 
     * always called once if {@link #internalBeforeStart(Map, Task)} is invoked and in the same thread as that method */
    protected void internalAfterEnd(Map<?,?> flags, Task<?> task, boolean startedInThisThread, boolean isEndingAllIterations) {
        if (log.isTraceEnabled()) log.trace(this+" afterEnd, task: "+task);
        if (startedInThisThread) {
            activeTaskCount.decrementAndGet();
        }
        if (isEndingAllIterations) {
            incompleteTaskIds.remove(task.getId());
            ExecutionUtils.invoke(flags.get("newTaskEndCallback"), task);
            ((TaskInternal<?>)task).setEndTimeUtc(System.currentTimeMillis());
        }

        if (startedInThisThread) {
            PerThreadCurrentTaskHolder.perThreadCurrentTask.remove();
            //clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
            if (RENAME_THREADS && startedInThisThread) {
                Thread thread = task.getThread();
                if (thread==null) {
                    log.warn("BasicTask.afterEnd invoked without corresponding beforeStart");
                } else {
                    thread.setName(threadOriginalName.get());
                    threadOriginalName.remove();
                }
            }
            ((TaskInternal<?>)task).setThread(null);
        }
        synchronized (task) { task.notifyAll(); }
    }

    public TaskScheduler getTaskSchedulerForTag(Object tag) {
        return schedulerByTag.get(tag);
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
                throw Exceptions.propagate(e);
            } catch (IllegalAccessException e) {
                throw Exceptions.propagate(e);
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
    
    @VisibleForTesting
    public ConcurrentMap<Object, TaskScheduler> getSchedulerByTag() {
        return schedulerByTag;
    }

}
