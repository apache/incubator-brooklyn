package brooklyn.util.task;

import java.util.Map
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

import brooklyn.management.ExecutionManager
import brooklyn.management.Task

/** TaskPreprocessor is the internal mechanism by which tasks are decorated so that they acquire a synch block,
 * or clear previous entrants, etc.
 */
public interface TaskPreprocessor {

	/** called by BasicExecutionManager when preprocessor is associated with an execution manager */
	public void injectManager(ExecutionManager m);
	/** called by BasicExecutionManager when preprocessor is associated with a tag */
	public void injectTag(Object tag);
	
	/** called by BasicExecutionManager when task is submitted in the category, in order of tags */ 
	public void onSubmit(Map flags, Task task);
	/** called by BasicExecutionManager when task is started in the category, in order of tags */ 
	public void onStart(Map flags, Task task);
	/** called by BasicExecutionManager when task is ended in the category, in _reverse_ order of tags */
	public void onEnd(Map flags, Task task);

}

/** Instances of this class ensures that tasks it is shown (through onSubmit, onStart, and onEnd) execute with in-order single-threaded semantics
 * (but not necessarily all in the same thread).  The order is that in which it is submitted.
 * <p>
 * This implementation does so by blocking on a {@link ConcurrentLinkedQueue}, _after_ the task is started in a thread (and Task.isStarted() returns true),
 * but (of course) _before_ the task.job actually gets invoked. */
public class SingleThreadedExecution implements TaskPreprocessor {
	Queue<String> order = new ConcurrentLinkedQueue<String>()
	
	ExecutionManager manager;
	Object tag;
	
	public void injectManager(ExecutionManager manager) {
		this.manager = manager;
	}
	public void injectTag(Object tag) {
		this.tag = tag;
	}
	public void onSubmit(Map flags=[:], Task task) {
		order.add(task.id)
	}
	public void onStart(Map flags=[:], Task task) {
		def next = order.peek();
		while (next!=task.id) {
			task.blockingDetails = "single threaded category, "+order.size()+" elements ahead of us when submitted"
			synchronized (task.id) {
				task.id.wait();
			}
			next = order.peek();
		}
		task.blockingDetails = null
	}
	public void onEnd(Map flags=[:], Task task) {
		def last = order.remove()
		assert last == task.id
		def next = order.peek()
		if (next!=null) synchronized (next) { next.notifyAll() }
	}
}
