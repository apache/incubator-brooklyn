package brooklyn.util.task;

import java.util.Map
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import brooklyn.management.ExecutionManager
import brooklyn.management.Task

/**
 * The preprocessor is an internal mechanism to decorate {@link Task}s.
 *
 * This can be used to enhance tasks that they acquire a {@code synchronized} block (to cause
 * them to effectively run singly-threaded), or clear intermediate queued jobs, etc.
 */
public interface TaskPreprocessor {
    /**
     * Called by {@link BasicExecutionManager} when preprocessor is associated with an
     * execution manager.
     */
    public void injectManager(ExecutionManager m);

    /**
     * Called by {@link BasicExecutionManager} when preprocessor is associated with a tag.
     */
    public void injectTag(Object tag);

    /**
     * Called by {@link BasicExecutionManager} when task is submitted in the category, in
     * order of tags.
     */
    public void onSubmit(Map flags, Task task);

    /**
     * Called by {@link BasicExecutionManager} when task is started in the category, in
     * order of tags.
     */
    public void onStart(Map flags, Task task);

    /**
     * Called by {@link BasicExecutionManager} when task is ended in the category, in
     * <em>reverse</em> order of tags.
     */
    public void onEnd(Map flags, Task task);

}
