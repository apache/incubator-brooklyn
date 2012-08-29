package brooklyn.entity.osgi.karaf

import java.util.Collection
import java.util.Map
import java.util.concurrent.TimeUnit

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJava
import brooklyn.entity.basic.UsesJmx
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.JmxPostProcessors
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.event.basic.MapConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

/**
 * This sets up a Karaf OSGi container
 */
public class KarafContainer extends SoftwareProcessEntity implements UsesJava, UsesJmx {
    
    // TODO Better way of setting/overriding defaults for config keys that are defined in super class SoftwareProcessEntity
    
    public static final String KARAF_ADMIN = "org.apache.karaf:type=admin,name=%s"
    public static final String KARAF_FEATURES = "org.apache.karaf:type=features,name=%s"

    @SetFromFlag("karafName")
    public static final BasicAttributeSensorAndConfigKey<String> KARAF_NAME = [ String, "karaf.name", "Karaf instance name", "root" ]

    // TODO too complicated? Used by KarafContainer; was in JavaApp; where should it be in brave new world?
    public static final MapConfigKey<Map> NAMED_PROPERTY_FILES = [ Map, "karaf.runtime.files", "Property files to be generated, referenced by name relative to runDir" ]

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey JMX_USER = [ Attributes.JMX_USER, "karaf" ]
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey JMX_PASSWORD = [ Attributes.JMX_PASSWORD, "karaf" ]
        
    @SetFromFlag("jmxPort")
    public static final PortAttributeSensorAndConfigKey JMX_PORT = new PortAttributeSensorAndConfigKey(UsesJmx.JMX_PORT, "1099+")

    @SetFromFlag("rmiServerPort")
    public static final PortAttributeSensorAndConfigKey RMI_SERVER_PORT = new PortAttributeSensorAndConfigKey(UsesJmx.RMI_SERVER_PORT, "44444+")
    @Deprecated // since 0.4 use RMI_SERVER_PORT
    public static final PortAttributeSensorAndConfigKey RMI_PORT = RMI_SERVER_PORT;
    
    @SetFromFlag("jmxContext")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = [ UsesJmx.JMX_CONTEXT, "karaf-"+KARAF_NAME.configKey.defaultValue ]

    @SetFromFlag("version")
    public static final BasicConfigKey SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "2.2.9" ]
    
    public static final BasicAttributeSensor<Map> KARAF_INSTANCES = [ Map, "karaf.admin.instances", "Karaf admin instances" ]
    public static final BasicAttributeSensor<Boolean> KARAF_ROOT = [ Boolean, "karaf.admin.isRoot", "Karaf admin isRoot" ]
    public static final BasicAttributeSensor<String> KARAF_JAVA_OPTS = [String, "karaf.admin.java_opts", "Karaf Java opts" ]
    public static final BasicAttributeSensor<String> KARAF_INSTALL_LOCATION  = [ String, "karaf.admin.location", "Karaf install location" ]
    public static final BasicAttributeSensor<Integer> KARAF_PID = [ Integer, "karaf.admin.pid", "Karaf instance PID" ]
    public static final BasicAttributeSensor<Integer> KARAF_SSH_PORT = [ Integer, "karaf.admin.ssh_port", "Karaf SSH Port" ]
    public static final BasicAttributeSensor<Integer> KARAF_RMI_REGISTRY_PORT = [ Integer, "karaf.admin.rmi_registry_port", "Karaf instance RMI registry port" ]
    public static final BasicAttributeSensor<Integer> KARAF_RMI_SERVER_PORT = [ Integer, "karaf.admin.rmi_server_port", "Karaf RMI (JMX) server port" ]
    public static final BasicAttributeSensor<String> KARAF_STATE = [ String, "karaf.admin.state", "Karaf instance state" ]

    protected JmxSensorAdapter jmxAdapter
    
    public KarafContainer(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public KarafSshDriver newDriver(SshMachineLocation machine) {
        return new KarafSshDriver(this, machine)
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        //FIXME should have a better way of setting config -- firstly, not here!
        //preferred style is to have config auto-applied to attributes, and have default values in their definition, not here
        //use of "properties.{user,password}" is non-standard; is that requried? use default jmxUser, jmxPassword flags?
        setAttribute(JMX_CONTEXT, String.format("karaf-%s", getConfig(KARAF_NAME.configKey)))
        
        sensorRegistry.register(new ConfigSensorAdapter());
        jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(period: 500*TimeUnit.MILLISECONDS));
        
        jmxAdapter.objectName(String.format(KARAF_ADMIN, getConfig(KARAF_NAME.configKey))).with {
            attribute("Instances").subscribe(KARAF_INSTANCES, JmxPostProcessors.tabularDataToMap())
        }
        
        // INSTANCES aggregates data for the other sensors.
        sensorRegistry.addSensor(SERVICE_UP, { getInstanceAttribute("State")?.equals("Started") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_ROOT, { getInstanceAttribute("Is Root") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_JAVA_OPTS, { getInstanceAttribute("JavaOpts") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_INSTALL_LOCATION, { getInstanceAttribute("Location") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_NAME, { getInstanceAttribute("Name") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_PID, { getInstanceAttribute("Pid") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_SSH_PORT, { getInstanceAttribute("Ssh Port") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_RMI_REGISTRY_PORT, { getInstanceAttribute("RMI Registry Port") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_RMI_SERVER_PORT, { getInstanceAttribute("RMI Server Port") } as ValueProvider)
        sensorRegistry.addSensor(KARAF_STATE, { getInstanceAttribute("State") } as ValueProvider)
    }

    protected <T> T getInstanceAttribute(String attribute) {
        return getAttribute(KARAF_INSTANCES)?.get(attribute)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(getConfig(RMI_PORT.configKey))
        return result
    }

    @Override
    public void postStart() {
		super.postStart()
        deployConfiguration(getConfig(NAMED_PROPERTY_FILES))
    }
    
    @Override
    protected void preStop() {
        if(jmxAdapter!=null){
            jmxAdapter.deactivateAdapter();
        }
        super.preStop();
    }

    protected void deployConfiguration(Map<String,Map<String,String>> propertyFiles) {
        Map<String,String> result = [:]
        
        // FIXME Store securely; may contain credentials!
        for (Map.Entry<String,Map<String,String>> entry in propertyFiles) {
            String file = entry.key
            Map<String,String> contents = entry.value

            Properties props = new Properties()
            for (Map.Entry<String,String> prop in contents) {
                props.setProperty(prop.key, prop.value)
            }
            
            File local = File.createTempFile(id, ".cfg")
            local.deleteOnExit() // just in case
            FileOutputStream fos = new FileOutputStream(local)
            try {
                props.store(fos, "Auto-generated by Brooklyn; " + file)
                fos.flush()
                File remote = new File(driver.runDir, file)
                driver.copyFile local, remote
            } finally {
                fos.close()
                local.delete()
            }
        }
    }
}
