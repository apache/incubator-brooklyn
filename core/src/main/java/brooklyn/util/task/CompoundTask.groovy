package brooklyn.util.task

import java.util.concurrent.Callable
import java.util.concurrent.Future

import brooklyn.management.ExecutionManager
import brooklyn.management.Task


/**
 * TODO: javadoc (splodge)
 */
public abstract class CompoundTask extends BasicTask<Object> {
	
	protected final Collection<Task> children;
	protected final Collection<Object> result;
	
	public CompoundTask(Object... jobs) {
		this( Arrays.asList(jobs) )
	}
	
	public CompoundTask(Collection<?> jobs) {
		super( tag:"compound" );
		super.job = { runJobs() }
		
		this.result = new ArrayList<Object>(jobs.size());
		this.children = new ArrayList<Task>(jobs.size());
		jobs.each { job ->
			if (job instanceof Task)          { children.add((Task) job) }
			else if (job instanceof Closure)  { children.add(new BasicTask((Closure) job)) }
			else if (job instanceof Callable) { children.add(new BasicTask((Callable) job)) }
			else if (job instanceof Runnable) { children.add(new BasicTask((Runnable) job)) }
			
			else throw new IllegalArgumentException("Invalid child "+job+
				" passed to compound task; must be Runnable, Callable, Closure or Task");
		}
	}

	/** return value needs to be specified by subclass */	
	protected abstract Object runJobs()
	
}
