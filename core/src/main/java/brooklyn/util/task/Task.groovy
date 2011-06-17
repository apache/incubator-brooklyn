package brooklyn.util.task;

import java.util.Collections.UnmodifiableSet;
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import brooklyn.util.internal.LanguageUtils


public class TaskStub {
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
 * Represents a task which is executing. The task can be given an optional displayName and description in its
 * constructor (as named arguments in the map in first parameter). When used with an ExecutionManager (or
 * ExecutionContext) it will record submission time, execution start time, end time, and any result. A task can be
 * submitted to the Execution{Manager,Context}, in which case it will be returned; or it may be created by submission
 * of a Runnable or Callable -- thereafter it can be treated just like a future. Additionally it is guaranteed to be
 * Object.notified once whenever the task starts running and once again when the task is about to complete
 * (due to the way executors work it is ugly to guarantee notification _after_ completion, so instead we notify just
 * before then expect user to call get() [which will throw errors if the underlying job did so] or blockUntilEnded()
 * [which will not throw errors]).
 */
public class Task<T> extends TaskStub implements Future<T> {
	private final Closure<T> job
	public final String displayName
	public final String description

		final Set tags = []

	public Task(Map flags=[:], Closure<T> job) {
		this.job = job
		description = flags.remove("description")
		displayName = flags.remove("displayName")
		
		if (flags.tag) tags.add flags.remove("tag")
		if (flags.tags) tags.addAll flags.remove("tags")
		
		if (flags) throw new IllegalArgumentException("Unsupported flags passed to task: "+flags)
	}
	public Task(Map flags=[:], Runnable job)    { this(flags, closureFromRunnable(job) ) }
	public Task(Map flags=[:], Callable<T> job) { this(flags, closureFromCallable(job) ) }

	public String toString() { "Task["+(displayName?displayName+(tags?"":";")+" ":"")+(tags?""+tags+"; ":"")+"$id]" }
	
	protected static <X> Closure<X> closureFromRunnable(Runnable r) {
		return {
			if (job in Callable) { job.call() }
			else { job.run(); null; }
		}
	}
	
	protected static <X> Closure<X> closureFromCallable(Callable<X> r) {
		return { job.call() }
	}
	
	// housekeeping --------------------
	
	private long submitTimeUtc = -1;
	private long startTimeUtc = -1;
	private long endTimeUtc = -1;
	private Task<?> submittedByTask;

	private boolean cancelled = false
	private Future<T> result
	
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
	
	public Task<?> getSubmittedByTask() { submittedByTask }
	public Future<T> getResultFuture() { result }
		
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

}
