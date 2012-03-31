package brooklyn.entity.basic.lifecycle;


/** In many cases it is cleaner to store entity lifecycle effectors (and sometimes other implementations) in a class to 
 * which the entity delegates.  Classes implementing this interface provide this delegate, often inheriting utilities
 * specific to a particular transport (e.g. ssh) shared among many different entities.
 * <p>
 * In this way, it is also possible for entities to cleanly support multiple mechanisms for start/stop and other methods. 
 **/
public interface StartStopDriver {

	public boolean isRunning();
	
	public void start();
	public void stop();
	public void restart();
		
}
