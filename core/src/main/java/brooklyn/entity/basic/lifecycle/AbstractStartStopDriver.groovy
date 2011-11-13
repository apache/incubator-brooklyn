package brooklyn.entity.basic.lifecycle;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.Location

public abstract class AbstractStartStopDriver implements StartStopDriver {

	private static final Logger log = LoggerFactory.getLogger(AbstractStartStopDriver.class);
	
    private final EntityLocal entityLocal;
    Location location;
    
    public AbstractStartStopDriver(EntityLocal entity, Location location) {
    	this.entityLocal = entity;
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

	@Override
	public abstract void stop();
	
	public abstract void install();
	public abstract void customize();
	public abstract void launch();
	
	@Override
	public void restart() {
		stop();
		start();
	}
	
	public EntityLocal getEntity() { entityLocal } 
	
	public Set<Integer> getPortsUsed() { [] }

	public InputStream getResource(String url) {
		try {
			if (url.matches("[A-Za-z]+:.*")) {
				//treat as URL
				if (url.startsWith("classpath:")) {
					url = url.substring(10);
					while (url.startsWith("/")) url=url.substring(1);
					return getClass().getClassLoader().getResourceAsStream(url);
				}
				return new URL(url).openStream()
			}
			//treat as file
			return new FileInputStream(url);
		} catch (Exception e) {
			log.warn "error opening ${url} for ${entity} (rethrowing): ${e}"
		}
	}
		
}
