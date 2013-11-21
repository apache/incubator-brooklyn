package brooklyn.entity.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.VisibleForTesting;

public class VanillaJavaAppImpl extends SoftwareProcessImpl implements VanillaJavaApp {

    private static final Logger log = LoggerFactory.getLogger(VanillaJavaApp.class);

    @SetFromFlag
    protected long jmxPollPeriod;
    
    protected JmxFeed jmxFeed;

    public VanillaJavaAppImpl() {}
    
    @VisibleForTesting
    public VanillaJavaAppImpl(Map<?,?> properties, Entity parent) {
        super(properties, parent);
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
        
        if ( ((VanillaJavaAppDriver)getDriver()).isJmxEnabled() ) {
            jmxPollPeriod = (jmxPollPeriod > 0) ? jmxPollPeriod : 500;
            jmxFeed = JavaAppUtils.connectMXBeanSensors(this, jmxPollPeriod);
        }

        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
    }
    
    @Override
    protected void preStop() {
        super.preStop();
    }

    @Override
    public Class<? extends VanillaJavaAppDriver> getDriverInterface() {
        return VanillaJavaAppDriver.class;
    }
    
    public String getRunDir() {
        // FIXME Make this an attribute; don't assume it hsa to be ssh? What uses this?
        VanillaJavaAppSshDriver driver = (VanillaJavaAppSshDriver) getDriver();
        return (driver != null) ? driver.getRunDir() : null;
    }

    @Override
    public void kill() {
        getDriver().kill();
    }
    
}
