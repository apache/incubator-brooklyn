package brooklyn.util.task

import java.lang.management.LockInfo
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.Collections.UnmodifiableSet
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.management.ExecutionManager
import brooklyn.management.Task
import brooklyn.management.TaskStub
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Objects

public class BasicTaskStub implements TaskStub {
    final String id = LanguageUtils.newUid()

    @Override
    public int hashCode() {
        return Objects.hashCode(id)
    }

    @Override
    public boolean equals(Object obj) {
        return LanguageUtils.equals(this, obj, "id")
    }

    @Override
    public String toString() { "Task[${id}]" }
}

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
 *
 * @see BasicTaskStub
 */
public class BasicTask<T> extends BasicTaskStub implements Task<T> {
    protected static final Logger log = LoggerFactory.getLogger(Task.class);

    protected Closure<T> job
    public final String displayName
    public final String description

    protected final Set tags = []

    String blockingDetails = null;

    /**
     * Constructor needed to prevent confusion in groovy stubs when looking for default constructor,
     *
     * The generics on {@link Closure} break it if that is first constructor.
     */
    protected BasicTask(Map flags=[:]) {
        this(flags, (Closure) null)
    }

    public BasicTask(Map flags=[:], Closure<T> job) {
        this.job = job

        if (flags.tag) tags.add flags.remove("tag")
        if (flags.tags) tags.addAll flags.remove("tags")

        description = flags.remove("description") ?: ""
        displayName = flags.remove("displayName") ?: tags.join("-")
    }

    public BasicTask(Map flags=[:], Runnable job)    { this(flags, closureFromRunnable(job) as Closure) }

    public BasicTask(Map flags=[:], Callable<T> job) { this(flags, closureFromCallable(job) as Closure) }

    @Override
    public String toString() { "Task["+(displayName?displayName+(tags?"":";")+" ":"")+(tags?""+tags+"; ":"")+"$id]" }

    protected static <X> Closure<X> closureFromRunnable(Runnable job) {
        return {
            if (job in Callable) { job.call() }
            else { job.run(); null; }
        }
    }

    protected static <X> Closure<X> closureFromCallable(Callable<X> job) {
        return { job.call() }
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

    protected long submitTimeUtc = -1;
    protected long startTimeUtc = -1;
    protected long endTimeUtc = -1;
    protected Task<?> submittedByTask;

    protected volatile Thread thread = null
    private volatile boolean cancelled = false
    private Future<T> result = null

    protected ExecutionManager em = null

    void initExecutionManager(ExecutionManager em) {
        this.em = em
    }

    synchronized void initResult(Future result) {
        if (this.result != null) throw new IllegalStateException("task ${this} is being given a result twice");
        this.result = result
        notifyAll()
    }

    // metadata accessors ------------

    public Set<Object> getTags() { new UnmodifiableSet(new LinkedHashSet(tags)) }
    public long getSubmitTimeUtc() { submitTimeUtc }
    public long getStartTimeUtc() { startTimeUtc }
    public long getEndTimeUtc() { endTimeUtc }

    public Future<T> getResult() { result }
    public Task<?> getSubmittedByTask() { submittedByTask }

    /** the thread where the task is running, if it is running */
    public Thread getThread() { thread }

    // basic fields --------------------

    public boolean isSubmitted() {
        return submitTimeUtc >= 0
    }

    public boolean isBegun() {
        return startTimeUtc >= 0
    }

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) return false
        boolean cancel = true
        if (result) { cancel = result.cancel(mayInterruptIfRunning) }
        cancelled = true
        notifyAll()
        return cancel
    }

    public boolean isCancelled() {
        cancelled || result?.isCancelled()
    }

    public boolean isDone() {
        cancelled || result?.isDone()
    }

    /**
     * Returns true if the task has had an error.
     *
     * Only true if calling {@link #get()} will throw an exception when it completes (including cancel).
     * Implementations may set this true before completion if they have that insight, or
     * (the default) they may compute it lazily after completion (returning false before completion).
     */
    public boolean isError() {
        if (!isDone()) return false
        if (isCancelled()) return true
        try {
            get()
            return false
        } catch (Throwable t) {
            return true
        }
    }

    public T get() throws InterruptedException, ExecutionException {
        blockUntilStarted()
        result.get()
    }

    // future value --------------------

    public synchronized void blockUntilStarted() {
        while (true) {
            if (cancelled) throw new CancellationException()
            if (result==null) wait()
            if (result!=null) return
        }
    }

    public void blockUntilEnded() {
        blockUntilStarted()
        try { result.get() } catch (Throwable t) { /* swallow errors when using this method */ }
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long start = System.currentTimeMillis()
        long milliseconds = TimeUnit.MILLISECONDS.convert(timeout, unit)
        long end  = start + milliseconds
	    while (end < System.currentTimeMillis()) {
            if (cancelled) throw new CancellationException()
            if (result == null) wait(end - System.currentTimeMillis())
            if (result != null) break
		}
        long remaining = end -  System.currentTimeMillis()
        if (remaining > 0) {
            return result.get(remaining, TimeUnit.MILLISECONDS)
        } else {
            throw new TimeoutException()
        }
    }

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
    public String getStatusSummary() {
        getStatusString(0)
    }

    /**
     * Returns detailed status, suitable for a hover
     *
     * Plain-text format, with new-lines (and sometimes extra info) if multiline enabled.
     */
    public String getStatusDetail(boolean multiline) {
        getStatusString(multiline?2:1)
    }

    /**
     * This method is useful for callers to see the status of a task.
     *
     * Also for developers to see best practices for examining status fields etc
     *
     * @param verbosity 0 = brief, 1 = one-line with some detail, 2 = lots of detail
     */
    protected String getStatusString(int verbosity) {
        volatile Thread t = getThread()
        String rv
        if (submitTimeUtc <= 0) rv = "Not submitted"
        else if (!isCancelled() && startTimeUtc <= 0) {
            rv = "Submitted for execution"
            if (verbosity>0) {
                long elapsed = System.currentTimeMillis() - submitTimeUtc;
                rv += " "+elapsed+" ms ago"
            }
        } else if (isDone()) {
            long elapsed = endTimeUtc - submitTimeUtc;
            String duration = ""+elapsed+" ms";
            rv = "Ended "
            if (isCancelled()) {
                rv += "by cancellation"
                if (verbosity >= 1) rv+" after "+duration;
            } else if (isError()) {
                rv += "by error"
                if (verbosity >= 1) {
                    rv += " after "+duration
                    Throwable error
                    try { String rvx = get(); error = "no error, return value $rvx" /* shouldn't happen */ }
                    catch (Throwable tt) { error = tt }

                    //remove outer ExecException which is reported by the get(), we want the exception the task threw
                    if (error in ExecutionException) error = error.getCause()

                    if (verbosity == 1) rv += " ("+error+")"
                    else {
                        StringWriter sw = new StringWriter()
                        error.printStackTrace(new PrintWriter(sw))
                        rv += "\n"+sw.getBuffer()
                    }
                }
            } else {
                rv += "normally"
                if (verbosity>=1) {
                    if (verbosity==1) {
                        rv += ", result "+get()
                    } else {
                        rv += " after "+duration
                        rv += "\n" + "Result: "+get()
                    }
                }
            }
        } else {
            //active
            if (t==null) t = getThread()  //possible race on entry with initialization, but should be resolved now (according to task.thread)
            assert t!=null : "shouldn't be possible not to have a current thread for $this as we were started and not ended, thread is "+t

            ThreadInfo ti = ManagementFactory.threadMXBean.getThreadInfo t.getId(), (verbosity<=0 ? 0 : verbosity==1 ? 1 : Integer.MAX_VALUE)
            if (getThread()==null)
                //thread might have moved on to a new task; if so, recompute (it should now say "done")
                return getStatusString(verbosity)
            LockInfo lock = ti.getLockInfo()
            if (!lock && ti.getThreadState()==Thread.State.RUNNABLE) {
                //not blocked
                if (ti.isSuspended()) {
                    // when does this happen?
                    rv = "Waiting"
                    if (verbosity >= 1) rv += ", thread suspended"
                } else {
                    rv = "Running"
                    if (verbosity >= 1) rv += " ("+ti.getThreadState()+")"
                }
            } else {
                rv = "Waiting"
                if (verbosity>=1) {
                    if (ti.getThreadState() == Thread.State.BLOCKED) {
                        rv += " (mutex) on "+lookup(lock)
                        //TODO could say who holds it
                    } else if (ti.getThreadState() == Thread.State.WAITING) {
                        rv += " (notify) on "+lookup(lock)
                    } else if (ti.getThreadState() == Thread.State.TIMED_WAITING) {
                        rv += " (timed) on "+lookup(lock)
                    } else {
                        rv = " ("+ti.getThreadState()+") on "+lookup(lock)
                    }
                    if (blockingDetails) rv += " - "+blockingDetails;
                }
            }
            if (verbosity>=2) {
                List<StackTraceElement> st = ti.getStackTrace()
                st = StackTraceSimplifier.cleanStackTrace(st)
                if (st!=null && st.size()>0)
                    rv += "\n" +"At: "+st[0]
                for (int ii=1; ii<st.size(); ii++) {
                    rv += "\n" +"    "+st[ii]
                }
            }
        }
        return rv
    }

    protected String lookup(LockInfo info) {
        return info ?: "unknown (sleep)"
    }

    public String getDisplayName() {
        return displayName

    }

    public String getDescription() {
        return description
    }
}
