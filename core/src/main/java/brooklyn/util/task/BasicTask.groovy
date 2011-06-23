package brooklyn.util.task

import groovy.lang.Closure

import java.lang.management.LockInfo
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.Map
import java.util.Set
import java.util.Collections.UnmodifiableSet
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import brooklyn.management.ExecutionManager
import brooklyn.management.Task
import brooklyn.management.TaskStub
import brooklyn.util.internal.LanguageUtils

public class BasicTaskStub implements TaskStub {
	final String id = LanguageUtils.newUid()
//	Object jvm = null //pointer to jvm where something is running, for distributed tasks
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (!(obj instanceof TaskStub)) return false;
		TaskStub other = (TaskStub) obj;
		if ((id == null) != (other.id == null)) return false;
		if (id != null && !id.equals(other.id)) return false;
		return true;
	}
	
	public String toString() { "TaskStub[$id]" }
	
}

/**
 * A task can be given an optional displayName and description in its constructor (as named 
 * arguments in the map in first parameter). It is guaranteed to be Object.notified 
 * once whenever the task starts running and once again when the task is about to complete (due to 
 * the way executors work it is ugly to guarantee notification _after_ completion, so instead we 
 * notify just before then expect user to call get() [which will throw errors if the underlying job 
 * did so] or blockUntilEnded() [which will not throw errors]).
 */
public class BasicTask<T> extends BasicTaskStub implements Task<T> {
	protected Closure<T> job
	public final String displayName
	public final String description

	protected final Set tags = []

	//constructor needed to prevent confusion in groovy stubs when looking for default constructor (generics on Closure<T> breaks it if that is first constructor)
	protected BasicTask(Map flags=[:]) {
		this(flags, (Closure)null)
	}
	public BasicTask(Map flags=[:], Closure<T> job) {
		this.job = job
		description = flags.remove("description")
		displayName = flags.remove("displayName")
		
		if (flags.tag) tags.add flags.remove("tag")
		if (flags.tags) tags.addAll flags.remove("tags")
		
		if (flags) throw new IllegalArgumentException("Unsupported flags passed to task: "+flags)
	}
	public BasicTask(Map flags=[:], Runnable job)    { this(flags, closureFromRunnable(job) as Closure) }
	public BasicTask(Map flags=[:], Callable<T> job) { this(flags, closureFromCallable(job) as Closure) }

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
	
	protected long submitTimeUtc = -1;
	protected long startTimeUtc = -1;
	protected long endTimeUtc = -1;
	protected Task<?> submittedByTask;

	protected Thread thread = null
	private boolean cancelled = false
	private Future<T> result = null
	protected ExecutionManager em = null
	
	void initExecutionManager(ExecutionManager em) {
		this.em = em
	}
	
	synchronized void initResult(Future result) {
		if (this.result!=null) throw new IllegalStateException("task "+this+" is being given a result twice");
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
		
	// future --------------------
	
	public synchronized boolean cancel(boolean mayInterruptIfRunning) {
		if (isDone()) return false
		boolean rv = true
		if (result) rv = result.cancel(mayInterruptIfRunning)
		cancelled = true
		notifyAll()
		rv
	}

	public boolean isCancelled() {
		cancelled || result?.isCancelled()
	}

	public boolean isDone() {
		cancelled || result?.isDone()
	}

	/**
	 * Returns true if the task has had an error; i.e. if calling get() will throw an exception when it completes
	 * (including cancel); implementations may set this true before completion if they have that insight, or
	 * (the default) they may compute it lazily after completion (returning false before completion).
	 */
	public boolean isError() {
		if (!isDone()) return false
		if (isCancelled()) return true
		try { get(); return false; } catch (Throwable t) { return true; }
	}
	
	public T get() throws InterruptedException, ExecutionException {
		blockUntilStarted()
		result.get()
	}

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
		//FIXME add support for timeouts
		get()
//
//		def v = Futures.run(collect { Future f -> { -> f.get(timeout, unit) } } )
//		v.collect { Future f -> f.get() }
	}
	
	/** returns a brief status string; plain-text format; reported status if there is one;
	 * otherwise state which will be one of:
	 * - Not submitted
	 * - Submitted for execution
	 * - Ended by error
	 * - Ended by cancellation
	 * - Ended normally
	 * - Running
	 * - Waiting
	 **/
	public String getStatusSummary() {
		getStatusString(0)
	}
	/** returns detailed status, suitable for a hover; plain-text format,
	 * with new-lines (and sometimes extra info) if multiline enabled */
	public String getStatusDetail(boolean multiline) {
		getStatusString(multiline?2:1)
	}
	/** this method is useful for callers to see the status (0=brief, 1=one-line with some detail, 2=lots of detail) of a task,
	 * and also for developers to see best practices for examining status fields etc */
	protected String getStatusString(int verbosity) {
		Thread t = getThread()
		String rv
		if (submitTimeUtc <= 0) rv = "Not submitted"
		else if (startTimeUtc <= 0) {
			rv = "Submitted for execution"
			if (verbosity>0) {
				long elapsed = System.currentTimeMillis() - submitTimeUtc;
				rv += " "+elapsed+" ms ago"
			}
		} else if (isDone()) {
			long elapsed = System.currentTimeMillis() - submitTimeUtc;
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
						error.printStackTrace new PrintWriter(sw), true
						rv += "\n"+sw.getBuffer()
					}
				}
			} else {
				rv += "normally"
				if (verbosity>=1) {
					if (verbosity==1) {
						rv += ", rv "+get()
					} else {
						rv += " after "+duration
						rv += "\n" + "rv: "+get()
					}
				}
			}
		} else {
			//active
			assert t!=null : "shouldn't be possible not to have a current thread as we were started and not ended"
			ThreadInfo ti = ManagementFactory.threadMXBean.getThreadInfo t.getId(), (verbosity<=0 ? 0 : verbosity==1 ? 1 : Integer.MAX_VALUE)
			if (getThread()==null)
				//thread might have moved on to a new task; if so, recompute (it should now say "done")
				return getStatusString(verbosity)
			LockInfo lock = ti.getLockInfo()
			if (!lock) {
				//not blocked
				if (ti.isSuspended()) {
					rv = "Waiting"
					if (verbosity >= 1) rv += ", thread suspended"
				} else {
					rv = "Running"
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
				}
			}
			if (verbosity>=2) {
				if (ti.getStackTrace()!=null && ti.getStackTrace().length>0)
					rv += "\n" +"At: "+ti.getStackTrace()[0]
				for (int ii=1; ii<ti.getStackTrace().length; ii++) {
					rv += "\n" +"    "+ti.getStackTrace()[ii]
				}
			}
		}
		return rv
	}
		
	protected lookup(LockInfo l) {
		return l
	}
}
