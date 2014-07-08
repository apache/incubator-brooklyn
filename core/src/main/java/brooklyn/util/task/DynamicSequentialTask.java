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

import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

/** Represents a task whose run() method can create other tasks
 * which are run sequentially, but that sequence runs in parallel to this task
 * <p>
 * There is an optional primary job run with this task, along with multiple secondary children.
 * If any secondary task fails (assuming it isn't {@link Tasks#markInessential()} then by default
 * subsequent tasks are not submitted and the primary task fails (but no tasks are cancelled or interrupted).
 * You can change the behavior of this task with fields in {@link FailureHandlingConfig},
 * or the convenience {@link TaskQueueingContext#swallowChildrenFailures()}
 * (and {@link DynamicTasks#swallowChildrenFailures()} if you are inside the task).
 * <p>
 * This synchronizes on secondary tasks when submitting them, in case they may be manually submitted
 * and the submitter wishes to ensure it is only submitted once.
 * <p>
 * Improvements which would be nice to have:
 * <li> unqueued tasks not visible in api; would like that
 * <li> uses an extra thread (submitted as background task) to monitor the secondary jobs; would be nice to remove this,
 *      and rely on {@link BasicExecutionManager} to run the jobs sequentially (combined with fix to item above)
 * <li> would be nice to have cancel, resume, and possibly skipQueue available as operations (ideally in the REST API and GUI)   
 **/
public class DynamicSequentialTask<T> extends BasicTask<T> implements HasTaskChildren, TaskQueueingContext {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final Queue<Task<?>> secondaryJobsAll = new ConcurrentLinkedQueue<Task<?>>();
    protected final Queue<Task<?>> secondaryJobsRemaining = new ConcurrentLinkedQueue<Task<?>>();
    protected final Object jobTransitionLock = new Object();
    protected volatile boolean primaryStarted = false;
    protected volatile boolean primaryFinished = false;
    protected volatile boolean secondaryQueueAborted = false;
    protected Thread primaryThread;
    protected DstJob dstJob;
    protected FailureHandlingConfig failureHandlingConfig = FailureHandlingConfig.DEFAULT;

    // default values for how to handle the various failures
    @Beta
    public static class FailureHandlingConfig {
        /** secondary queue runs independently of primary task (submitting and blocking on each secondary task in order), 
         * but can set it up not to submit any more tasks if the primary fails */
        public final boolean abortSecondaryQueueOnPrimaryFailure;
        /** as {@link #abortSecondaryQueueOnPrimaryFailure} but controls cancelling of secondary queue*/
        public final boolean cancelSecondariesOnPrimaryFailure;
        /** secondary queue can continue submitting+blocking tasks even if a secondary task fails (unusual;
         * typically handled by {@link TaskTags#markInessential(Task)} on the secondary tasks, in which case
         * the secondary queue is never aborted */
        public final boolean abortSecondaryQueueOnSecondaryFailure;
        /** unsubmitted secondary tasks (ie those further in the queue) can be cancelled if a secondary task fails */
        public final boolean cancelSecondariesOnSecondaryFailure;
        /** whether to issue cancel against primary task if a secondary task fails */
        public final boolean cancelPrimaryOnSecondaryFailure;
        /** whether to fail this task if a secondary task fails */
        public final boolean failParentOnSecondaryFailure;
        
        @Beta
        public FailureHandlingConfig(
                boolean abortSecondaryQueueOnPrimaryFailure, boolean cancelSecondariesOnPrimaryFailure,
                boolean abortSecondaryQueueOnSecondaryFailure, boolean cancelSecondariesOnSecondaryFailure,
                boolean cancelPrimaryOnSecondaryFailure, boolean failParentOnSecondaryFailure) {
            this.abortSecondaryQueueOnPrimaryFailure = abortSecondaryQueueOnPrimaryFailure;
            this.cancelSecondariesOnPrimaryFailure = cancelSecondariesOnPrimaryFailure;
            this.abortSecondaryQueueOnSecondaryFailure = abortSecondaryQueueOnSecondaryFailure;
            this.cancelSecondariesOnSecondaryFailure = cancelSecondariesOnSecondaryFailure;
            this.cancelPrimaryOnSecondaryFailure = cancelPrimaryOnSecondaryFailure;
            this.failParentOnSecondaryFailure = failParentOnSecondaryFailure;
        }
        
        public static final FailureHandlingConfig DEFAULT = new FailureHandlingConfig(false, false, true, false, false, true);
        public static final FailureHandlingConfig SWALLOWING_CHILDREN_FAILURES = new FailureHandlingConfig(false, false, false, false, false, false);
    }
    
    
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
        this.job = dstJob = new DstJob(mainJob);
    }
    
    @Override
    public void queue(Task<?> t) {
        synchronized (jobTransitionLock) {
            if (primaryFinished)
                throw new IllegalStateException("Cannot add a task to "+this+" when it is already finished (trying to add "+t+")");
            secondaryJobsAll.add(t);
            secondaryJobsRemaining.add(t);
            BrooklynTaskTags.addTagsDynamically(t, ManagementContextInternal.SUB_TASK_TAG);
            ((TaskInternal<?>)t).markQueued();
            jobTransitionLock.notifyAll();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel(mayInterruptIfRunning, mayInterruptIfRunning, true);
    }
    public boolean cancel(boolean mayInterruptTask, boolean interruptPrimaryThread, boolean alsoCancelChildren) {
        if (isDone()) return false;
        log.trace("cancelling {}", this);
        boolean cancel = super.cancel(mayInterruptTask);
        if (alsoCancelChildren) {
            for (Task<?> t: secondaryJobsAll)
                cancel |= t.cancel(mayInterruptTask);
        }
        synchronized (jobTransitionLock) {
            if (primaryThread!=null) {
                if (interruptPrimaryThread) {
                    log.trace("cancelling {} - interrupting", this);
                    primaryThread.interrupt();
                }
                cancel = true;
            }
        }
        return cancel;
    }
    
    @Override
    public synchronized boolean uncancel() {
        secondaryQueueAborted = false;
        return super.uncancel();
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
        synchronized (task) {
            if (task.isSubmitted() && !task.isDone())
                log.debug("DST "+this+" skipping submission of child "+task+" because it is already submitted");
            else
                ec.submit(task);
        }
    }

    public void setFailureHandlingConfig(FailureHandlingConfig failureHandlingConfig) {
        this.failureHandlingConfig = failureHandlingConfig;
    }
    @Override
    public void swallowChildrenFailures() {
        setFailureHandlingConfig(FailureHandlingConfig.SWALLOWING_CHILDREN_FAILURES);
    }
    
    protected class DstJob implements Callable<T> {
        protected Callable<T> primaryJob;
        /** currently executing (or just completed) secondary task, or null if none;
         * with jobTransitionLock notified on change and completion */
        protected volatile Task<?> currentSecondary = null;
        protected volatile boolean finishedSecondaries = false;
        
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
            // TODO overkill having a thread/task for this, but it works
            // optimisation would either use newTaskEndCallback property on task to submit
            // or use some kind of single threaded executor for the queued tasks
            Task<List<Object>> secondaryJobMaster = Tasks.<List<Object>>builder().dynamic(false)
                    .name("DST manager (internal)")
                    .body(new Callable<List<Object>>() {

                @Override
                public List<Object> call() throws Exception {
                    List<Object> result = new ArrayList<Object>();
                    try { 
                        while (!secondaryQueueAborted && (!primaryFinished || !secondaryJobsRemaining.isEmpty())) {
                            synchronized (jobTransitionLock) {
                                if (!primaryFinished && secondaryJobsRemaining.isEmpty()) {
                                    currentSecondary = null;
                                    jobTransitionLock.wait(1000);
                                }
                            }
                            @SuppressWarnings("rawtypes")
                            Task secondaryJob = secondaryJobsRemaining.poll();
                            if (secondaryJob != null) {
                                synchronized (jobTransitionLock) {
                                    currentSecondary = secondaryJob;
                                    submitBackgroundInheritingContext(secondaryJob);
                                    jobTransitionLock.notifyAll();
                                }
                                try {
                                    result.add(secondaryJob.get());
                                } catch (Exception e) {
                                    if (TaskTags.isInessential(secondaryJob)) {
                                        result.add(Tasks.getError(secondaryJob));
                                        if (log.isDebugEnabled())
                                            log.debug("Secondary job queue for "+DynamicSequentialTask.this+" ignoring error in inessential task "+secondaryJob+": "+e);
                                    } else {
                                        if (failureHandlingConfig.cancelSecondariesOnSecondaryFailure) {
                                            if (log.isDebugEnabled())
                                                log.debug("Secondary job queue for "+DynamicSequentialTask.this+" cancelling "+secondaryJobsRemaining.size()+" remaining, due to error in task "+secondaryJob+": "+e);
                                            synchronized (jobTransitionLock) {
                                                for (Task<?> t: secondaryJobsRemaining)
                                                    t.cancel(true);
                                                jobTransitionLock.notifyAll();
                                            }
                                        }
                                        
                                        if (failureHandlingConfig.abortSecondaryQueueOnSecondaryFailure) {
                                            if (log.isDebugEnabled())
                                                log.debug("Aborting secondary job queue for "+DynamicSequentialTask.this+" due to error in child task "+secondaryJob+" ("+e+", being rethrown)");
                                            secondaryQueueAborted = true;
                                            throw e;
                                        }

                                        if (!primaryFinished && failureHandlingConfig.cancelPrimaryOnSecondaryFailure) {
                                            cancel(true, false, false);
                                        }
                                        
                                        result.add(Tasks.getError(secondaryJob));
                                        if (log.isDebugEnabled())
                                            log.debug("Secondary job queue for "+DynamicSequentialTask.this+" continuing in presence of error in child task "+secondaryJob+" ("+e+", being remembered)");
                                    }
                                }
                            }
                        }
                    } finally {
                        synchronized (jobTransitionLock) {
                            currentSecondary = null;
                            finishedSecondaries = true;
                            jobTransitionLock.notifyAll();
                        }
                    }
                    return result;
                }
            }).build();
            submitBackgroundInheritingContext(secondaryJobMaster);
            
            T result = null;
            Throwable error=null;
            boolean errorIsFromChild=false;
            try {
                log.trace("calling primary job for {}", this);
                if (primaryJob!=null) result = primaryJob.call();
            } catch (Throwable selfException) {
                Exceptions.propagateIfFatal(selfException);
                error = selfException;
                errorIsFromChild = false;
                if (failureHandlingConfig.abortSecondaryQueueOnPrimaryFailure) {
                    if (log.isDebugEnabled())
                        log.debug("Secondary job queue for "+DynamicSequentialTask.this+" aborting with "+secondaryJobsRemaining.size()+" remaining, due to error in primary task: "+selfException);
                    secondaryQueueAborted = true;
                }
                if (failureHandlingConfig.cancelSecondariesOnPrimaryFailure) {
                    if (log.isDebugEnabled())
                        log.debug(DynamicSequentialTask.this+" cancelling "+secondaryJobsRemaining.size()+" remaining, due to error in primary task: "+selfException);
                    synchronized (jobTransitionLock) {
                        for (Task<?> t: secondaryJobsRemaining)
                            t.cancel(true);
                        // do this early to prevent additions; and note we notify very soon below, so not notify is help off until below
                        primaryThread = null;
                        primaryFinished = true;
                    }
                }
            } finally {
                try {
                    log.trace("cleaning up for {}", this);
                    synchronized (jobTransitionLock) {
                        // semaphore might be nicer here (aled notes as it is this is a little hard to read)
                        primaryThread = null;
                        primaryFinished = true;
                        jobTransitionLock.notifyAll();
                    }
                    if (!isCancelled() && !Thread.currentThread().isInterrupted()) {
                        log.trace("waiting for secondaries for {}", this);
                        // wait on tasks sequentially so that blocking information is more interesting
                        DynamicTasks.waitForLast();
                        List<Object> result2 = secondaryJobMaster.get();
                        try {
                            if (primaryJob==null) result = (T)result2;
                        } catch (ClassCastException e) { /* ignore class cast exception; leave the result as null */ }
                    }
                } catch (Throwable childException) {
                    Exceptions.propagateIfFatal(childException);
                    if (error==null) {
                        error = childException;
                        errorIsFromChild = true;
                    } else {
                        log.debug("Parent task "+this+" ignoring child error ("+childException+") in presence of our own error ("+error+")");
                    }
                }
            }
            if (error!=null)
                handleException(error, errorIsFromChild);
            return result;
        }
        
        @Override
        public String toString() {
            return "DstJob:"+DynamicSequentialTask.this;
        }

        /** waits for this job to complete, or the given time to elapse */
        public void join(boolean includePrimary, Duration optionalTimeout) throws InterruptedException {
            CountdownTimer timeLeft = optionalTimeout!=null ? CountdownTimer.newInstanceStarted(optionalTimeout) : null;
            while (true) {
                Task<?> cs;
                Duration remaining;
                synchronized (jobTransitionLock) {
                    cs = currentSecondary;
                    if (finishedSecondaries) return;
                    remaining = timeLeft==null ? Duration.ONE_SECOND : timeLeft.getDurationRemaining();
                    if (!remaining.isPositive()) return;
                    if (cs==null) {
                        if (!includePrimary && secondaryJobsRemaining.isEmpty()) return;
                        // parent still running, no children though
                        Tasks.setBlockingTask(DynamicSequentialTask.this);
                        jobTransitionLock.wait(remaining.toMilliseconds());
                        Tasks.resetBlockingDetails();
                    }
                }
                if (cs!=null) {
                    Tasks.setBlockingTask(cs);
                    cs.blockUntilEnded(remaining);
                    Tasks.resetBlockingDetails();
                }
            }
        }
    }

    @Override
    public List<Task<?>> getQueue() {
        return ImmutableList.copyOf(secondaryJobsAll);
    }

    public void handleException(Throwable throwable, boolean fromChild) throws Exception {
        Exceptions.propagateIfFatal(throwable);
        if (fromChild && !failureHandlingConfig.failParentOnSecondaryFailure) {
            log.debug("Parent task "+this+" swallowing child error: "+throwable);
            return;
        }
        handleException(throwable);
    }
    public void handleException(Throwable throwable) throws Exception { 
        Exceptions.propagateIfFatal(throwable);
        if (throwable instanceof Exception) {
            // allow checked exceptions to be passed through
            throw (Exception)throwable;
        }
        throw Exceptions.propagate(throwable);
    }

    @Override @Deprecated
    public Task<?> last() {
        List<Task<?>> l = getQueue();
        if (l.isEmpty()) return null;
        return l.get(l.size()-1);
    }

    @Override
    public void drain(Duration optionalTimeout, boolean includePrimary, boolean throwFirstError) {
        try {
            dstJob.join(includePrimary, optionalTimeout);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        if (throwFirstError) {
            if (isError()) 
                getUnchecked();
            for (Task<?> t: getQueue())
                if (t.isError() && !TaskTags.isInessential(t))
                    t.getUnchecked();
        }
    }

}
