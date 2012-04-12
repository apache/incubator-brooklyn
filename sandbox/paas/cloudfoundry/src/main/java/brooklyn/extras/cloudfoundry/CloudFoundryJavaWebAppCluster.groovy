package brooklyn.extras.cloudfoundry

import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import groovy.lang.MetaClass;

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.BasicConfigurableEntityFactory
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.event.adapter.FunctionSensorAdapter
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStats
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

class CloudFoundryJavaWebAppCluster extends AbstractEntity implements Startable, Resizable, ElasticJavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryJavaWebAppCluster.class)
    
    @SetFromFlag("appName")
    public static final BasicConfigKey<String> APP_NAME = [ String, "cloudfoundry.app.name.uid", "Unique name for this app" ]

    public static final BasicAttributeSensor<String> HOSTNAME = [ String, "cloudfoundry.host.name", "The hostname where the app should be accessed" ];

//    @SetFromFlag("url")            
//    public static final BasicConfigKey<String> URL = [ String, "cloudfoundry.app.url", "URL this app should respond to" ]
    
    public static final SERVICE_STATE = Attributes.SERVICE_STATE
    
    public static final BasicAttributeSensor<Integer> SIZE = [ Integer, "cloudfoundry.instances.size", "Number of instances" ];
    public static final BasicAttributeSensor<Double> CPU_USAGE = [ Double, "cloudfoundry.cpu.usage", "Average CPU utilisation" ];
    public static final BasicAttributeSensor<Double> MEMORY_USED_FRACTION = [ Double, "cloudfoundry.memory.usage.fraction", "Average memory utilisation" ];

    protected transient SensorRegistry sensorRegistry;
    
    //TODO other cloudfoundries
    private final String domain = "cloudfoundry.com";
    
    public CloudFoundryJavaWebAppCluster(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        setAttribute(SERVICE_UP, false)
        setAttribute(SERVICE_STATE, Lifecycle.CREATED);
        sensorRegistry = new SensorRegistry(this);
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
        if (getConfig(APP_NAME)!=appName) setConfigInternal(APP_NAME, appName);
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
        connectSensors();
    }
    
    public void connectSensors() {
        String hostname = appName+"."+domain;
        setAttribute(HOSTNAME, hostname);
        setAttribute(ROOT_URL, "http://"+hostname+"/");
        sensorRegistry.register(new FunctionSensorAdapter({cfAccess.stats()})).with {
            poll(SIZE, { CloudFoundryAppStats stats -> stats.instances.size() });
            poll(CPU_USAGE, { CloudFoundryAppStats stats -> stats.average.cpuUsage });
            poll(MEMORY_USED_FRACTION, { CloudFoundryAppStats stats -> stats.average.memUsedFraction });
        }
        sensorRegistry.activateAdapters();
    }

    public String getWebAppAddress() {
        return cfAccess.getUrl();
    }
    
    public void destroy() {
        log.info "{} destroying app {}", this, getAppName()
        sensorRegistry.deactivateAdapters();
        cfAccess.destroyApp();
    }

    @Override
    public Integer resize(Integer desiredSize) {
        cfAccess.resizeAbsolute(desiredSize);
        //TODO could block until SIZE sensor report achieved, or timeout ?
    }

    @Override
    public Integer getCurrentSize() {
        return getAttribute(SIZE);
    }

    
    public static class Factory extends BasicConfigurableEntityFactory<CloudFoundryJavaWebAppCluster> {
        public Factory(Map flags=[:]) { super(flags, CloudFoundryJavaWebAppCluster) }
    }

}
