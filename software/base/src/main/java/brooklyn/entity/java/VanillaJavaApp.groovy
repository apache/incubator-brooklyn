package brooklyn.entity.java

import groovy.time.TimeDuration

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJava
import brooklyn.entity.basic.UsesJavaMXBeans
import brooklyn.entity.basic.UsesJmx
import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver
import brooklyn.entity.basic.lifecycle.ScriptHelper
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.FunctionSensorAdapter
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.ResourceUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.StringEscapeUtils


public class VanillaJavaApp extends SoftwareProcessEntity implements UsesJava, UsesJmx, UsesJavaMXBeans {

    // FIXME classpath values: need these to be downloaded and installed?
    
    // TODO Make jmxPollPeriod @SetFromFlag easier to use: currently a confusion over long and TimeDuration, and 
    // no ability to set default value (can't just set field because config vals read/set in super-constructor :-(
     
    private static final Logger log = LoggerFactory.getLogger(VanillaJavaApp.class)
    
    @SetFromFlag("args")
    public static final BasicConfigKey<List> ARGS = [ List, "vanillaJavaApp.args", "Arguments for launching the java app", 
        [] ]
    
    @SetFromFlag(value="main", nullable=false)
    public static final BasicConfigKey<String> MAIN_CLASS = [ String, "vanillaJavaApp.mainClass", "class to launch" ];

    @SetFromFlag("classpath")
    public static final BasicConfigKey<List> CLASSPATH = [ List, "vanillaJavaApp.classpath", "classpath to use, as list of URL entries", 
        [] ];

    @SetFromFlag(defaultVal="true")
    protected boolean useJmx;
    
    @SetFromFlag
    protected long jmxPollPeriod
    
    @SetFromFlag("jvmXArgs")
    public static final BasicConfigKey<List> JVM_XARGS = [ List, "vanillaJavaApp.jvmXArgs", "JVM -X args for the java app (e.g. memory)", 
        ["-Xms128m", "-Xmx512m", "-XX:MaxPermSize=512m"] ];

    @SetFromFlag("jvmDefines")
    public static final BasicConfigKey<Map> JVM_DEFINES = [ Map, "vanillaJavaApp.jvmDefines", "JVM system property definitions for the app",
        [:] ];

    protected JmxSensorAdapter jmxAdapter
    
    public VanillaJavaApp(Map props=[:], Entity owner=null) {
        super(props, owner)
    }
    
    public String getMainClass() { getConfig(MAIN_CLASS); }
    public List getClasspath() { getConfig(CLASSPATH); }
    public Map getJvmDefines() { getConfig(JVM_DEFINES); }
    public List getJvmXArgs() { getConfig(JVM_XARGS); }

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
            TimeDuration jmxPollPeriod = (jmxPollPeriod > 0 ? jmxPollPeriod : 500)*TimeUnit.MILLISECONDS
            jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(period:jmxPollPeriod));
            JavaAppUtils.connectMXBeanSensors(this, jmxAdapter)
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

    public VanillaJavaAppSshDriver newDriver(SshMachineLocation loc) {
        new VanillaJavaAppSshDriver(this, loc)
    }
}

public class VanillaJavaAppSshDriver extends JavaStartStopSshDriver {

    public VanillaJavaAppSshDriver(VanillaJavaApp entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public VanillaJavaApp getEntity() { super.getEntity() }

    public boolean isJmxEnabled() { super.isJmxEnabled() && entity.useJmx }
    
    protected String getLogFileLocation() {
        return "$runDir/console"
    }
    
    @Override
    public void install() {
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
            failOnNonZeroResultCode().
            body.append("mkdir -p $runDir/lib").
            execute();
        ResourceUtils r = new ResourceUtils(entity);
        for (String f: entity.classpath) {
            // TODO if it's a local folder then JAR it up before sending?
            // TODO support wildcards
            int result = machine.installTo(new ResourceUtils(entity), f, runDir+"/"+"lib"+"/");
            if (result!=0)
                throw new IllegalStateException("unable to install classpath entry $f for $entity at $machine");
            // if it's a zip or tgz then expand
                
            // FIXME dedup with code in machine.installTo above
            String destName = f;
            destName = destName.contains('?') ? destName.substring(0, destName.indexOf('?')) : destName;
            destName = destName.substring(destName.lastIndexOf('/')+1);
            
            if (destName.toLowerCase().endsWith(".zip")) {
                result = machine.run("cd $runDir/lib && unzip $destName");
            } else if (destName.toLowerCase().endsWith(".tgz") || destName.toLowerCase().endsWith(".tar.gz")) {
                result = machine.run("cd $runDir/lib && tar xvfz $destName");
            } else if (destName.toLowerCase().endsWith(".tar")) {
                result = machine.run("cd $runDir/lib && tar xvfz $destName");
            }
            if (result!=0)
                throw new IllegalStateException("unable to install classpath entry $f for $entity at $machine (failed to expand archive)");
        }
    }
    
    @Override
    public void launch() {
        String clazz = entity.mainClass;
        String args = entity.getConfig(VanillaJavaApp.ARGS).collect({
            StringEscapeUtils.assertValidForDoubleQuotingInBash(it);
            return "\""+it+"\"";
        }).join(" ");
    
        newScript(LAUNCHING, usePidFile:true).
            body.append(
                "echo \"launching: java \$JAVA_OPTS -cp \'lib/*\' $clazz $args\"",
                "java \$JAVA_OPTS -cp \"lib/*\" $clazz $args "+
                    " >> $runDir/console 2>&1 </dev/null &",
            ).execute();
    }
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile: true)
                .execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(STOPPING, usePidFile: true)
                .execute();
    }

    @Override
    protected Map getCustomJavaSystemProperties() { 
        return super.getCustomJavaSystemProperties() + entity.jvmDefines }
    
    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return super.getCustomJavaConfigOptions() + entity.jvmXArgs;
    }
}
