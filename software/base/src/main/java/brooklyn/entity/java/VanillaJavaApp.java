package brooklyn.entity.java;

import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.collections.MutableList;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ImplementedBy(VanillaJavaAppImpl.class)
public interface VanillaJavaApp extends SoftwareProcess, UsesJava, UsesJmx, UsesJavaMXBeans {

    // FIXME classpath values: need these to be downloaded and installed?
    
    // TODO Make jmxPollPeriod @SetFromFlag easier to use: currently a confusion over long and TimeDuration, and 
    // no ability to set default value (can't just set field because config vals read/set in super-constructor :-(
         
    @SetFromFlag("args")
    public static final ConfigKey<List> ARGS = new BasicConfigKey<List>(List.class, "vanillaJavaApp.args", "Arguments for launching the java app", Lists.newArrayList());
    
    @SetFromFlag(value="main", nullable=false)
    public static final ConfigKey<String> MAIN_CLASS = new BasicConfigKey<String>(String.class, "vanillaJavaApp.mainClass", "class to launch");

    @SetFromFlag("classpath")
    public static final ConfigKey<List> CLASSPATH = new BasicConfigKey<List>(List.class, "vanillaJavaApp.classpath", "classpath to use, as list of URL entries; "
        + "these URLs are copied to lib/ (expanded in the case of tar/tgz/zip), with 'lib/*' used at runtime", Lists.newArrayList());

    @SetFromFlag("jvmXArgs")
    public static final ConfigKey<List> JVM_XARGS = new BasicConfigKey<List>(List.class, "vanillaJavaApp.jvmXArgs", "JVM -X args for the java app (e.g. memory)", 
        MutableList.of("-Xms128m", "-Xmx512m", "-XX:MaxPermSize=512m"));

    @SetFromFlag("jvmDefines")
    public static final ConfigKey<Map> JVM_DEFINES = new BasicConfigKey<Map>(Map.class, "vanillaJavaApp.jvmDefines", "JVM system property definitions for the app",
        Maps.newLinkedHashMap());

    
    public String getMainClass();
    public List<String> getClasspath();
    public Map getJvmDefines();
    public List getJvmXArgs();
    public String getRunDir();

    public void kill();
    
}
