package brooklyn.util.task;

import static brooklyn.util.JavaGroovyEquivalents.asString;
import static brooklyn.util.JavaGroovyEquivalents.elvisString;
import static brooklyn.util.JavaGroovyEquivalents.join;
import groovy.lang.Closure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ExecutionManager;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * The basic concrete implementation of a {@link Task} to be executed.
 *
 * A {@link Task} is a wrapper for an executable unit, such as a {@link Closure} or a {@link Runnable} or
 * {@link Callable} and will run in its own {@link Thread}.
 * <p>
 * The task can be given an optional displayName and description in its constructor (as named
 * arguments in the first {@link Map} parameter). It is guaranteed to have {@link Object#notify()} called
 * once whenever the task starts running and once again when the task is about to complete. Due to
 * the way executors work it is ugly to guarantee notification <em>after</em> completion, so instead we
 * notify just before then expect the user to call {@link #get()} - which will throw errors if the underlying job
 * did so - or {@link #blockUntilEnded()} which will not throw errors.
 */
public class BasicTask<T> implements TaskInternal<T> {
    private static final Logger log = LoggerFactory.getLogger(BasicTask.class);

    private String id = Identifiers.makeRandomId(8);
    protected Callable<T> job;
    public final String displayName;
    public final String description;

    protected final Set<Object> tags = new LinkedHashSet<Object>();

    protected String blockingDetails = null;
    protected Task<?> blockingTask = null;
    Object extraStatusText = null;

    protected final ExecutionList listeners = new ExecutionList();
    
    /**
     * Constructor needed to prevent confusion in groovy stubs when looking for default constructor,
     *
     * The generics on {@link Closure} break it if that is first constructor.
     */
    protected BasicTask() { this(Collections.emptyMap()); }
    protected BasicTask(Map<?,?> flags) { this(flags, (Callable<T>) null); }

    public BasicTask(Callable<T> job) { this(Collections.emptyMap(), job); }
    
    public BasicTask(Map<?,?> flags, Callable<T> job) {
        this.job = job;

        if (flags.containsKey("tag")) tags.add(flags.remove("tag"));
        Object ftags = flags.remove("tags");
        if (ftags!=null) {
            if (ftags instanceof Iterable) Iterables.addAll(tags, (Iterable<?>)ftags);
            else {
                log.info("deprecated use of non-collection argument for 'tags' ("+ftags+") in "+this, new Throwable("trace of discouraged use of non-colleciton tags argument"));
                tags.add(ftags);
            }
        }

        description = elvisString(flags.remove("description"), "");
        String d = asString(flags.remove("displayName"));
        if (d==null) d = join(tags, "-");
        displayName = d;
    }

    public BasicTask(Runnable job) { this(GroovyJavaMethods.<T>callableFromRunnable(job)); }
    public BasicTask(Map<?,?> flags, Runnable job) { this(flags, GroovyJavaMethods.<T>callableFromRunnable(job)); }
    public BasicTask(Closure<T> job) { this(GroovyJavaMethods.callableFromClosure(job)); }
    public BasicTask(Map<?,?> flags, Closure<T> job) { this(flags, GroovyJavaMethods.callableFromClosure(job)); }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Task)
            return ((Task<?>)obj).getId().equals(getId());
        return false;
    }

    @Override
    public String toString() { 
        return "Task["+(displayName!=null && displayName.length()>0?displayName+
                (tags!=null && !tags.isEmpty()?"":";")+" ":"")+
                (tags!=null && !tags.isEmpty()?tags+"; ":"")+getId()+"]";
    }

    @Override
    public Task<T> asTask() {
        return this;
    }
    
    // housekeeping --------------------

    /*
     * These flags are set by BasicExecutionManager.submit.
     *
     * Order is guaranteed to be as shown below, in order of #. Within each # line it is currently in the order specified by commas but this is not guaranteed.
     * (The spaces between the # section indicate longer delays / logical separation ... it should be clear!)
     *
     * # submitter, submit time set, tags and other submit-time fields set, task tag-linked preprocessors onSubmit invoked
     *
     * # thread set, ThreadLocal getCurrentTask set
     * # start time set, isBegun is true
     * # task tag-linked preprocessors onStart invoked
     * # task end callback run, if supplied
     *
     * # task runs
     *
     * # task end callback run, if supplied
     * # task tag-linked preprocessors onEnd invoked (in reverse order of tags)
     * # end time set
     * # thread cleared, ThreadLocal getCurrentTask set
     * # Task.notifyAll()
     * # Task.get() (result.get()) available, Task.isDone is true
     *
     * Few _consumers_ should care, but internally we rely on this so that, for example, status is displayed correctly.
     * Tests should catch most things, but be careful if you change any of the above semantics.
     */

    protected long queuedTimeUtc = -1;
    protected long submitTimeUtc = -1;
    protected long startTimeUtc = -1;
    protected long endTimeUtc = -1;
    protected Task<?> submittedByTask;

    protected volatile Thread thread = null;
    private volatile boolean cancelled = false;
    protected volatile Future<T> result = null;
    
    @Override
    public synchronized void initResult(ListenableFuture<T> result) {
        if (this.result != null) 
            throw new IllegalStateException("task "+this+" is being given a result twice");
        this.result = result;
        notifyAll();
    }

    // metadata accessors ------------

    @Override
    public Set<Object> getTags() { return Collections.unmodifiableSet(new LinkedHashSet<Object>(tags)); }
    
    /** if the job is queued for submission (e.g. by another task) it can indicate that fact (and time) here;
     * note tasks can (and often are) submitted without any queueing, in which case this value may be -1 */
    @Override
    public long getQueuedTimeUtc() { return queuedTimeUtc; }
    
    @Override
    public long getSubmitTimeUtc() { return submitTimeUtc; }
    
    @Override
    public long getStartTimeUtc() { return startTimeUtc; }
    
    @Override
    public long getEndTimeUtc() { return endTimeUtc; }

    @Override
    public Future<T> getResult() { return result; }
    
    @Override
    public Task<?> getSubmittedByTask() { return submittedByTask; }

    /** the thread where the task is running, if it is running */
    @Override
    public Thread getThread() { return thread; }

    // basic fields --------------------

    @Override
    public boolean isQueuedOrSubmitted() {
        return (queuedTimeUtc >= 0) || isSubmitted();
    }

    @Override
    public boolean isQueuedAndNotSubmitted() {
        return (queuedTimeUtc >= 0) && (!isSubmitted());
    }

    @Override
    public boolean isSubmitted() {
        return submitTimeUtc >= 0;
    }

    @Override
    public boolean isBegun() {
        return startTimeUtc >= 0;
    }

    /** marks the task as queued for execution */
    @Override
    public void markQueued() {
        if (queuedTimeUtc<0)
            queuedTimeUtc = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean cancel() { return cancel(true); }
    
    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) return false;
        boolean cancel = true;
        cancelled = true;
        if (result!=null) { 
            cancel = result.cancel(mayInterruptIfRunning);
        }
        notifyAll();
        return cancel;
    }

    @Override
    public boolean isCancelled() {
        return cancelled || (result!=null && result.isCancelled());
    }

    @Override
    public boolean isDone() {
        return cancelled || (result!=null && result.isDone());
    }

    /**
     * Returns true if the task has had an error.
     *
     * Only true if calling {@link #get()} will throw an exception when it completes (including cancel).
     * Implementations may set this true before completion if they have that insight, or
     * (the default) they may compute it lazily after completion (returning false before completion).
     */
    @Override
    public boolean isError() {
        if (!isDone()) return false;
        if (isCancelled()) return true;
        try {
            get();
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    // future value --------------------

    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            if (!isDone())
                Tasks.setBlockingTask(this);
            blockUntilStarted();
            return result.get();
        } finally {
            Tasks.resetBlockingTask();
        }
    }

    @Override
    public T getUnchecked() {
        try {
            return get();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public synchronized void blockUntilStarted() {
        blockUntilStarted(null);
    }
    
    @Override
    public synchronized boolean blockUntilStarted(Duration timeout) {
        Long endTime = timeout==null ? null : System.currentTimeMillis() + timeout.toMillisecondsRoundingUp();
        while (true) {
            if (cancelled) throw new CancellationException();
            if (result==null)
                try {
                    if (timeout==null) {
                        wait();
                    } else {
                        long remaining = endTime - System.currentTimeMillis();
                        if (remaining>0)
                            wait(remaining);
                        else
                            return false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Throwables.propagate(e);
                }
            if (result!=null) return true;
        }
    }

    @Override
    public void blockUntilEnded() {
        blockUntilEnded(null);
    }
    
    @Override
    public boolean blockUntilEnded(Duration timeout) {
        Long endTime = timeout==null ? null : System.currentTimeMillis() + timeout.toMillisecondsRoundingUp();
        try { 
            boolean started = blockUntilStarted(timeout);
            if (!started) return false;
            if (timeout==null) {
                result.get();
            } else {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining>0)
                    result.get(remaining, TimeUnit.MILLISECONDS);
            }
            return isDone();
        } catch (Throwable t) {
            if (log.isDebugEnabled())
                log.debug("call from "+Thread.currentThread()+" blocking until "+this+" finishes ended with error: "+t);
            /* contract is just to log errors at debug, otherwise do nothing */
            return isDone(); 
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return get(new Duration(timeout, unit));
    }
    
    @Override
    public T get(Duration duration) throws InterruptedException, ExecutionException, TimeoutException {
        long start = System.currentTimeMillis();
        Long end  = duration==null ? null : start + duration.toMillisecondsRoundingUp();
        while (end==null || end > System.currentTimeMillis()) {
            if (cancelled) throw new CancellationException();
            if (result == null) {
                synchronized (this) {
                    long remaining = end - System.currentTimeMillis();
                    if (result==null && remaining>0)
                        wait(remaining);
                }
            }
            if (result != null) break;
        }
        Long remaining = end==null ? null : end -  System.currentTimeMillis();
        if (isDone()) {
            return result.get(1, TimeUnit.MILLISECONDS);
        } else if (remaining == null) {
            return result.get();
        } else if (remaining > 0) {
            return result.get(remaining, TimeUnit.MILLISECONDS);
        } else {
            throw new TimeoutException();
        }
    }

    @Override
    public T getUnchecked(Duration duration) {
        try {
            return get(duration);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    // ------------------ status ---------------------------
    
    /**
     * Returns a brief status string
     *
     * Plain-text format. Reported status if there is one, otherwise state which will be one of:
     * <ul>
     * <li>Not submitted
     * <li>Submitted for execution
     * <li>Ended by error
     * <li>Ended by cancellation
     * <li>Ended normally
     * <li>Running
     * <li>Waiting
     * </ul>
     */
    @Override
    public String getStatusSummary() {
        return getStatusString(0);
    }

    /**
     * Returns detailed status, suitable for a hover
     *
     * Plain-text format, with new-lines (and sometimes extra info) if multiline enabled.
     */
    @Override
    public String getStatusDetail(boolean multiline) {
        return getStatusString(multiline?2:1);
    }

    /**
     * This method is useful for callers to see the status of a task.
     *
     * Also for developers to see best practices for examining status fields etc
     *
     * @param verbosity 0 = brief, 1 = one-line with some detail, 2 = lots of detail
     */
    protected String getStatusString(int verbosity) {
//        Thread t = getThread();
        String rv;
        if (submitTimeUtc <= 0) rv = "Not submitted";
        else if (!isCancelled() && startTimeUtc <= 0) {
            rv = "Submitted for execution";
            if (verbosity>0) {
                long elapsed = System.currentTimeMillis() - submitTimeUtc;
                rv += " "+Time.makeTimeStringRoundedSince(elapsed)+" ago";
            }
            if (verbosity >= 2 && getExtraStatusText()!=null) {
                rv += "\n\n"+getExtraStatusText();
            }
        } else if (isDone()) {
            long elapsed = endTimeUtc - submitTimeUtc;
            String duration = Time.makeTimeStringRounded(elapsed);
            rv = "Ended ";
            if (isCancelled()) {
                rv += "by cancellation";
                if (verbosity >= 1) rv+=" after "+duration;
                
                if (verbosity >= 2 && getExtraStatusText()!=null) {
                    rv += "\n\n"+getExtraStatusText();
                }
            } else if (isError()) {
                rv += "by error";
                if (verbosity >= 1) {
                    rv += " after "+duration;
                    Object error;
                    try { String rvx = ""+get(); error = "no error, return value "+rvx; /* shouldn't happen */ }
                    catch (Throwable tt) { error = tt; }

                    if (verbosity >= 2 && getExtraStatusText()!=null) {
                        rv += "\n\n"+getExtraStatusText();
                    }
                    
                    //remove outer ExecException which is reported by the get(), we want the exception the task threw
                    while (error instanceof ExecutionException) error = ((Throwable)error).getCause();
                    String errorMessage = null;
                    if (error instanceof Throwable) errorMessage = ((Throwable)error).getMessage();
                    if (errorMessage==null || errorMessage.isEmpty()) errorMessage = ""+error;

                    if (verbosity >= 1) rv += ": "+errorMessage;
                    if (verbosity >= 2) {
                        StringWriter sw = new StringWriter();
                        ((Throwable)error).printStackTrace(new PrintWriter(sw));
                        rv += "\n\n"+sw.getBuffer();
                    }
                }
            } else {
                rv += "normally";
                if (verbosity>=1) {
                    if (verbosity==1) {
                        try {
                            Object v = get();
                            rv += ", " +(v==null ? "no return value (null)" : "result: "+v);
                        } catch (Exception e) {
                            rv += ", but error accessing result ["+e+"]"; //shouldn't happen
                        }
                    } else {
                        rv += " after "+duration;
                        try {
                            Object v = get();
                            rv += "\n\n" + (v==null ? "No return value (null)" : "Result: "+v);
                        } catch (Exception e) {
                            rv += " at first\n" +
                            		"Error accessing result ["+e+"]"; //shouldn't happen
                        }
                        if (verbosity >= 2 && getExtraStatusText()!=null) {
                            rv += "\n\n"+getExtraStatusText();
                        }
                    }
                }
            }
        } else {
			rv = getActiveTaskStatusString(verbosity);
        }
        return rv;
    }

	protected String getActiveTaskStatusString(int verbosity) {
		String rv = "";
		Thread t = getThread();
	
		// Normally, it's not possible for thread==null as we were started and not ended
		
		// However, there is a race where the task starts sand completes between the calls to getThread()
		// at the start of the method and this call to getThread(), so both return null even though
		// the intermediate checks returned started==true isDone()==false.
		if (t == null) {
			if (isDone()) {
				return getStatusString(verbosity);
			} else {
			    //should only happen for repeating task which is not active
                return "Sleeping";
			}
		}

		ThreadInfo ti = ManagementFactory.getThreadMXBean().getThreadInfo(t.getId(), (verbosity<=0 ? 0 : verbosity==1 ? 1 : Integer.MAX_VALUE));
		if (getThread()==null)
			//thread might have moved on to a new task; if so, recompute (it should now say "done")
			return getStatusString(verbosity);
        
        if (verbosity >= 1 && GroovyJavaMethods.truth(blockingDetails)) {
            if (verbosity==1)
                // short status string will just show blocking details
                return blockingDetails;
            //otherwise show the blocking details, then a new line, then additional information
            rv = blockingDetails + "\n\n";
        }
        
        if (verbosity >= 1 && GroovyJavaMethods.truth(blockingTask)) {
            if (verbosity==1)
                // short status string will just show blocking details
                return "Waiting on "+blockingTask;
            //otherwise show the blocking details, then a new line, then additional information
            rv = "Waiting on "+blockingTask + "\n\n";
        }

		if (verbosity>=2) {
            if (getExtraStatusText()!=null) {
                rv += getExtraStatusText()+"\n\n";
            }
            
		    rv += ""+toString()+"\n";
		    if (submittedByTask!=null) {
		        rv += "Submitted by "+submittedByTask+"\n";
		    }

		    if (this instanceof HasTaskChildren) {
		        // list children tasks for compound tasks
		        try {
		            Iterable<Task<?>> childrenTasks = ((HasTaskChildren)this).getChildren();
		            if (childrenTasks.iterator().hasNext()) {
		                rv += "Children:\n";
		                for (Task<?> child: childrenTasks) {
		                    rv += "  "+child+": "+child.getStatusDetail(false)+"\n";
		                }
		            }
		        } catch (ConcurrentModificationException exc) {
		            rv += "  (children not available - currently being modified)\n";
		        }
		    }
//		    // TODO spawned tasks would be interesting, but hard to retrieve
//		    // as we store execution context in thread local for the _task_;
//		    // it isn't actually stored on the task itself so not available to
//		    // 3rd party threads calling this extended toString method
		    rv += "\n";
		}
		
		LockInfo lock = ti.getLockInfo();
		if (!GroovyJavaMethods.truth(lock) && ti.getThreadState()==Thread.State.RUNNABLE) {
			//not blocked
			if (ti.isSuspended()) {
				// when does this happen?
				rv += "Waiting";
				if (verbosity >= 1) rv += ", thread suspended";
			} else {
				rv += "Running";
				if (verbosity >= 1) rv += " ("+ti.getThreadState()+")";
			}
		} else {
			rv += "Waiting";
			if (verbosity>=1) {
				if (ti.getThreadState() == Thread.State.BLOCKED) {
					rv += " (mutex) on "+lookup(lock);
					//TODO could say who holds it
				} else if (ti.getThreadState() == Thread.State.WAITING) {
					rv += " (notify) on "+lookup(lock);
				} else if (ti.getThreadState() == Thread.State.TIMED_WAITING) {
					rv += " (timed) on "+lookup(lock);
				} else {
					rv = " ("+ti.getThreadState()+") on "+lookup(lock);
				}
			}
		}
		if (verbosity>=2) {
			StackTraceElement[] st = ti.getStackTrace();
			st = StackTraceSimplifier.cleanStackTrace(st);
			if (st!=null && st.length>0)
				rv += "\n" +"At: "+st[0];
			for (int ii=1; ii<st.length; ii++) {
				rv += "\n" +"    "+st[ii];
			}
		}
		return rv;
	}
	
    protected String lookup(LockInfo info) {
        return GroovyJavaMethods.truth(info) ? ""+info : "unknown (sleep)";
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    
    /** allows a task user to specify why a task is blocked; for use immediately before a blocking/wait,
     * and typically cleared immediately afterwards; referenced by management api to inspect a task
     * which is blocking
     */
    @Override
    public void setBlockingDetails(String blockingDetails) {
        this.blockingDetails = blockingDetails;
    }
    
    @Override
    public void setBlockingTask(Task<?> blockingTask) {
        this.blockingTask = blockingTask;
    }
    
    @Override
    public void resetBlockingDetails() {
        this.blockingDetails = null;
    }
    
    @Override
    public void resetBlockingTask() {
        this.blockingTask = null;
    }

    /** returns a textual message giving details while the task is blocked */
    @Override
    public String getBlockingDetails() {
        return blockingDetails;
    }
    
    /** returns a task that this task is blocked on */
    @Override
    public Task<?> getBlockingTask() {
        return blockingTask;
    }
    
    @Override
    public void setExtraStatusText(Object extraStatus) {
        this.extraStatusText = extraStatus;
    }
    
    @Override
    public Object getExtraStatusText() {
        return extraStatusText;
    }

    // ---- add a way to warn if task is not run
    
    public interface TaskFinalizer {
        public void onTaskFinalization(Task<?> t);
    }

    public static final TaskFinalizer WARN_IF_NOT_RUN = new TaskFinalizer() {
        @Override
        public void onTaskFinalization(Task<?> t) {
            if (!t.isDone()) {
                // shouldn't happen
                log.warn("Task "+this+" is being finalized before completion");
                return;
            }
            if (!Tasks.isAncestorCancelled(t) && !t.isSubmitted()) {
                log.warn("Task "+this+" was never submitted; did the code forget to run it?");
            }
        }
    };

    public static final TaskFinalizer NO_OP = new TaskFinalizer() {
        @Override
        public void onTaskFinalization(Task<?> t) {
        }
    };
    
    public void ignoreIfNotRun() {
        setFinalizer(NO_OP);
    }
    
    public void setFinalizer(TaskFinalizer f) {
        TaskFinalizer finalizer = Tasks.tag(this, TaskFinalizer.class, false);
        if (finalizer!=null && finalizer!=f)
            throw new IllegalStateException("Cannot apply multiple finalizers");
        if (isDone())
            throw new IllegalStateException("Finalizer cannot be set on task "+this+" after it is finished");
        tags.add(f);
    }

    @Override
    protected void finalize() throws Throwable {
        TaskFinalizer finalizer = Tasks.tag(this, TaskFinalizer.class, false);
        if (finalizer==null) finalizer = WARN_IF_NOT_RUN;
        finalizer.onTaskFinalization(this);
    }
    
    @Override
    public void addListener(Runnable listener, Executor executor) {
        listeners.add(listener, executor);
    }
    
    @Override
    public void runListeners() {
        listeners.execute();
    }
    
    @Override
    public void setEndTimeUtc(long val) {
        endTimeUtc = val;
    }
    
    @Override
    public void setThread(Thread thread) {
        this.thread = thread;
    }
    
    @Override
    public Callable<T> getJob() {
        return job;
    }
    
    @Override
    public void setJob(Callable<T> job) {
        this.job = job;
    }
    
    @Override
    public ExecutionList getListeners() {
        return listeners;
    }
    
    @Override
    public void setSubmitTimeUtc(long val) {
        submitTimeUtc = val;
    }
    
    @Override
    public void setSubmittedByTask(Task<?> task) {
        submittedByTask = task;
    }
    
    @Override
    public Set<Object> getMutableTags() {
        return tags;
    }
    
    @Override
    public void setStartTimeUtc(long val) {
        startTimeUtc = val;
    }

    @Override
    public void applyTagModifier(Function<Set<Object>,Void> modifier) {
        modifier.apply(tags);
    }
    
}
