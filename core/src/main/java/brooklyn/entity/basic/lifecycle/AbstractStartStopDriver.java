package brooklyn.entity.basic.lifecycle;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.util.ResourceUtils;

public abstract class AbstractStartStopDriver implements StartStopDriver {

	private static final Logger log = LoggerFactory.getLogger(AbstractStartStopDriver.class);
	
    protected final EntityLocal entity;
    private final Location location;
    
    public AbstractStartStopDriver(EntityLocal entity, Location location) {
    	this.entity = checkNotNull(entity, "entity");
    	this.location = checkNotNull(location, "location");
    }
	
    /**
     * Start the entity.
     *
     * this installs, configures and launches the application process. However,
     * users can also call the {@link #install()}, {@link #customize()} and
     * {@link #launch()} steps independently. The {@link #postLaunch()} will
     * be called after the {@link #launch()} metheod is executed, but the
     * process may not be completely initialised at this stage, so care is
     * required when implementing these stages.
     *
     * @see #stop()
     */
	@Override
	public void start() {
        waitForConfigKey(ConfigKeys.INSTALL_LATCH);
		install();
        
        waitForConfigKey(ConfigKeys.CUSTOMIZE_LATCH);
		customize();
        
        waitForConfigKey(ConfigKeys.LAUNCH_LATCH);
		launch();
        
        postLaunch();  
	}

	@Override
	public abstract void stop();
	
	public abstract void install();
	public abstract void customize();
	public abstract void launch();
    
    /**
     * Implement this method in child classes to add some post-launch behavior
     */
	public void postLaunch() {}
    
	@Override
	public void restart() {
		stop();
		start();
	}
	
	public EntityLocal getEntity() { return entity; } 

	public Location getLocation() { return location; } 
	
    public InputStream getResource(String url) {
        return new ResourceUtils(entity).getResourceFromUrl(url);
    }
		
    protected void waitForConfigKey(ConfigKey<?> configKey) {
        Object val = entity.getConfig(configKey);
        if (val != null) log.debug("{} finished waiting for {} (value {}); continuing...", new Object[] {this, configKey, val});
    }
}
