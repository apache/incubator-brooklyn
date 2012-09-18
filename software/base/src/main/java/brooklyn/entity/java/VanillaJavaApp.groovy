package brooklyn.entity.java;

import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.FunctionSensorAdapter
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicConfigKey

import brooklyn.util.flags.SetFromFlag

public class VanillaJavaApp extends SoftwareProcessEntity implements UsesJava, UsesJmx, UsesJavaMXBeans {

    // FIXME classpath values: need these to be downloaded and installed?
    
    // TODO Make jmxPollPeriod @SetFromFlag easier to use: currently a confusion over long and TimeDuration, and 
    // no ability to set default value (can't just set field because config vals read/set in super-constructor :-(
     
    private static final Logger log = LoggerFactory.getLogger(VanillaJavaApp.class)
    
    @SetFromFlag("args")
    public static final ConfigKey<List> ARGS = new BasicConfigKey<List>(List.class, "vanillaJavaApp.args", "Arguments for launching the java app", []);
    
    @SetFromFlag(value="main", nullable=false)
    public static final ConfigKey<String> MAIN_CLASS = new BasicConfigKey<String>(String.class, "vanillaJavaApp.mainClass", "class to launch");

    @SetFromFlag("classpath")
    public static final ConfigKey<List> CLASSPATH = new BasicConfigKey<List>(List.class, "vanillaJavaApp.classpath", "classpath to use, as list of URL entries", []);

    @SetFromFlag(defaultVal="true")
    protected boolean useJmx;
    
    @SetFromFlag
    protected long jmxPollPeriod;
    
    @SetFromFlag("jvmXArgs")
    public static final ConfigKey<List> JVM_XARGS = new BasicConfigKey<List>(List.class, "vanillaJavaApp.jvmXArgs", "JVM -X args for the java app (e.g. memory)", 
        ["-Xms128m", "-Xmx512m", "-XX:MaxPermSize=512m"]);

    @SetFromFlag("jvmDefines")
    public static final ConfigKey<Map> JVM_DEFINES = new BasicConfigKey<Map>(Map.class, "vanillaJavaApp.jvmDefines", "JVM system property definitions for the app",
        [:]);

    protected JmxSensorAdapter jmxAdapter;
    
    public VanillaJavaApp(Map props=[:], Entity owner=null) {
        super(props, owner);
    }
    
    public String getMainClass() { return getConfig(MAIN_CLASS); }
    public List<String> getClasspath() { return getConfig(CLASSPATH); }
    public Map getJvmDefines() { return getConfig(JVM_DEFINES); }
    public List getJvmXArgs() { return getConfig(JVM_XARGS); }

    public void addToClasspath(String url) {
        List<String> cp = getConfig(CLASSPATH);
        List<String> newCP = new ArrayList<String>();
        if (cp!=null) newCP.addAll(cp);
        newCP.add(url);
        setConfig(CLASSPATH, newCP);
    }
    
    public void addToClasspath(Collection<String> urls) {
        List<String> cp = getConfig(CLASSPATH);
        List<String> newCP = new ArrayList<String>();
        if (cp!=null) newCP.addAll(cp);
        newCP.addAll(urls);
        setConfig(CLASSPATH, newCP);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        sensorRegistry.register(new ConfigSensorAdapter());
        
        if (useJmx) {
            TimeDuration jmxPollPeriod = (jmxPollPeriod > 0 ? jmxPollPeriod : 500)*TimeUnit.MILLISECONDS;
            jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(period:jmxPollPeriod));
            JavaAppUtils.connectMXBeanSensors(this, jmxAdapter);
        }
        
        FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
            period:10*TimeUnit.SECONDS, 
            { driver.isRunning() } ));
        serviceUpAdapter.poll(SERVICE_UP);
    }
    
    @Override
    protected void preStop() {
        jmxAdapter?.deactivateAdapter();
        super.preStop();
    }

    Class getDriverInterface() {
        return VanillaJavaAppDriver.class;
    }
    
    public String getRunDir() {
        // FIXME Make this an attribute
        return getDriver()?.getRunDir();
    }
}
