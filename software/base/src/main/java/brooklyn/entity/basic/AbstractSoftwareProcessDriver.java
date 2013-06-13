package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.location.Location;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * An abstract implementation of the {@link SoftwareProcessDriver}.
 */
public abstract class AbstractSoftwareProcessDriver implements SoftwareProcessDriver {

	private static final Logger log = LoggerFactory.getLogger(AbstractSoftwareProcessDriver.class);
	
    protected final EntityLocal entity;
    private final Location location;
    
    public AbstractSoftwareProcessDriver(EntityLocal entity, Location location) {
        this.entity = checkNotNull(entity, "entity");
        this.location = checkNotNull(location, "location");
    }
	
    /*
     * (non-Javadoc)
     * @see brooklyn.entity.basic.SoftwareProcessDriver#rebind()
     */
    @Override
    public void rebind() {
        // no-op
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
    
    @Override
    public void kill() {
        stop();
    }
    
    /**
     * Implement this method in child classes to add some post-launch behavior
     */
	public void postLaunch() {}
    
	@Override
	public void restart() {
	    boolean previouslyRunning = isRunning();
        try {
            stop();
        } catch (Exception e) {
            if (previouslyRunning) {
                log.debug(getEntity() + " restart: stop failed, when was previously running", e);
            } else {
                log.debug(getEntity() + " restart: stop failed (but was not previously running, so not a surprise)", e);
            }
        }
        launch();
	}
	
	public EntityLocal getEntity() { return entity; } 

	public Location getLocation() { return location; } 
    
    public InputStream getResource(String url) {
        return new ResourceUtils(entity).getResourceFromUrl(url);
    }
    
    public String getResourceAsString(String url) {
        return new ResourceUtils(entity).getResourceAsString(url);
    }

    public String processTemplate(File templateConfigFile, Map<String,Object> extraSubstitutions) {
        return processTemplate(templateConfigFile.toURI().toASCIIString(),extraSubstitutions);
    }

    public String processTemplate(File templateConfigFile) {
        return processTemplate(templateConfigFile.toURI().toASCIIString());
    }

    public String processTemplate(String templateConfigUrl) {
        return processTemplate(templateConfigUrl, Collections.EMPTY_MAP);
    }

    public String processTemplate(String templateConfigUrl, Map<String,? extends Object> extraSubstitutions) {
        Map<String, Object> config = getEntity().getApplication().getManagementContext().getConfig().asMapWithStringKeys();
        Map<String, Object> substitutions = ImmutableMap.<String, Object>builder()
                .putAll(config)
                .put("entity", entity)
                .put("driver", this)
                .put("location", getLocation())
                .putAll(extraSubstitutions)
                .build();

        try {
            String templateConfigFile = getResourceAsString(templateConfigUrl);

            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", templateConfigFile);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();

            return new String(baos.toByteArray());
        } catch (Exception e) {
            log.warn("Error creating configuration file for "+entity, e);
            throw Exceptions.propagate(e);
        }
    }
		
    protected void waitForConfigKey(ConfigKey<?> configKey) {
        Object val = entity.getConfig(configKey);
        if (val != null) log.debug("{} finished waiting for {} (value {}); continuing...", new Object[] {this, configKey, val});
    }
}
