package brooklyn.entity.basic.lifecycle;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;

public abstract class AbstractStartStopHelper implements StartStopHelper {

    EntityLocal entity;
    Location location;
    
    public AbstractStartStopHelper(EntityLocal entity, Location location) {
    	this.entity = entity;
    	this.location = location;
    }
	
    /**
     * Start the entity.
     *
     * this installs, configures and launches the application process. However,
     * users can also call the {@link #install()}, {@link #config()} and
     * {@link #runApp()} steps independently. The {@link #postStart()} method
     * will be called after the application run script has been executed, but
     * the process may not be completely initialised at this stage, so care is
     * required when implementing these stages.
     *
     * @see #stop()
     */
	@Override
	public void start() {
		install();
		customize();
		launch();
	}

	public void install() {}
	public void customize() {}
	public abstract void launch();
	
	@Override
	public void restart() {
		stop();
		start();
	}
	
}
