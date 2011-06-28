package brooklyn.management;

import java.util.Set;
import java.util.concurrent.Future;


/**
 * Represents a unit of work for execution. When used with an ExecutionManager (or
 * ExecutionContext) it will record submission time, execution start time, end time, and any result. A task can be
 * submitted to the Execution{Manager,Context}, in which case it will be returned; or it may be created by submission
 * of a Runnable or Callable -- thereafter it can be treated just like a future.
 */
public interface Task<T> extends TaskStub, Future<T> {

    public Set<Object> getTags();
    public long getSubmitTimeUtc();
    public long getStartTimeUtc();
    public long getEndTimeUtc();
    
    public Task<?> getSubmittedByTask();
    /** the thread where the task is running, if it is running */
    public Thread getThread();

    /** whether task has started running; will remain true after normal completion or non-cancellation error;
     * will be true on cancel iff the thread did actually start */
    public boolean isBegun();
    /** whether the task threw an error, including cancellation (implies isDone) */
    public boolean isError();

    /** causes calling thread to block until the task is started */
    public void blockUntilStarted();
    /** causes calling thread to block until the task is ended (normally or by cancellation or error, but without throwing error on cancellation or error) */
    public void blockUntilEnded();
    
    public String getStatusSummary();
    /** returns detailed status, suitable for a hover; plain-text format,
     * with new-lines (and sometimes extra info) if multiline enabled */
    public String getStatusDetail(boolean multiline);
    
}
