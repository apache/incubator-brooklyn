package brooklyn.management;

import java.util.Set;
import java.util.concurrent.Future;


/**
 * Represents a unit of work for execution. When used with an ExecutionManager (or
 * ExecutionContext) it will record submission time, execution start time, end time, and any result. A task can be
 * submitted to the Execution{Manager,Context}, in which case it will be returned; or it may be created by submission
 * of a Runnable or Callable -- thereafter it can be treated just like a future. Additionally it is guaranteed to be
 * Object.notified once whenever the task starts running and once again when the task is about to complete
 * (due to the way executors work it is ugly to guarantee notification _after_ completion, so instead we notify just
 * before then expect user to call get() [which will throw errors if the underlying job did so] or blockUntilEnded()
 * [which will not throw errors]).
 */
public interface Task<T> extends TaskStub, Future<T> {

	public Set<Object> getTags();
	public long getSubmitTimeUtc();
	public long getStartTimeUtc();
	public long getEndTimeUtc();
	
	public Task<?> getSubmittedByTask();
	/** the thread where the task is running, if it is running */
	public Thread getThread();

	public boolean isError();

	public void blockUntilStarted();
	public void blockUntilEnded();
	
	public String getStatusSummary();
	/** returns detailed status, suitable for a hover; plain-text format,
	 * with new-lines (and sometimes extra info) if multiline enabled */
	public String getStatusDetail(boolean multiline);
	
}
