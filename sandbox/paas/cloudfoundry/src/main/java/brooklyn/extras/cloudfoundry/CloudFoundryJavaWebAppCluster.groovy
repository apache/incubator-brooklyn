package brooklyn.extras.cloudfoundry

import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

class CloudFoundryJavaWebAppCluster extends AbstractEntity implements Startable, JavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryJavaWebAppCluster.class)
    
    @SetFromFlag("appName")
    public static final BasicConfigKey<String> APP_NAME = [ String, "cloudfoundry.app.name.uid", "Unique name for this app" ]

//    @SetFromFlag("url")            
//    public static final BasicConfigKey<String> URL = [ String, "cloudfoundry.app.url", "URL this app should respond to" ]
    
    public static final SERVICE_STATE = Attributes.SERVICE_STATE

    public CloudFoundryJavaWebAppCluster(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        setAttribute(SERVICE_UP, false)
        setAttribute(SERVICE_STATE, Lifecycle.CREATED);
    }

    public String getAppName() {
        def appName = getConfig(APP_NAME);
        if (appName) return appName;
        return "brooklyn-"+getId();
    }    
    
    public String getWar() {
        return getConfig(ROOT_WAR);
    }
    
    public void start(Collection<Location> locations) {
        startInLocation locations
    }

    public void startInLocation(Collection<Location> locations) {
        Preconditions.checkArgument locations.size() == 1
        Location location = Iterables.getOnlyElement(locations)
        startInLocation(location)
    }

    public void restart() {
        stop()
        start()
    }
    public void stop() {
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        destroy()
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
    }
    
    private transient CloudFoundryVmcCliAccess _cfAccess;
    public CloudFoundryVmcCliAccess getCfAccess() {
        if (_cfAccess!=null) return _cfAccess;
        _cfAccess = new CloudFoundryVmcCliAccess(
            appName:getAppName(), war: war, context: this)
    }
    
    public void startInLocation(CloudFoundryLocation ol) {
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        locations << ol
        
        if (!war) throw new IllegalStateException("A WAR file is required to start ${this}")

        cfAccess.runAppWar();
        log.info "{} app launched: {}", this, getAppName()

        //add support for DynamicWebAppCluster.startInLocation(CloudFoundry)
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
    }

    public String getWebAppAddress() {
        return cfAccess.getUrl();
    }
    
    public void destroy() {
        log.info "{} destroying app {}", this, getAppName()
        cfAccess.destroyApp();
    }
    
}
