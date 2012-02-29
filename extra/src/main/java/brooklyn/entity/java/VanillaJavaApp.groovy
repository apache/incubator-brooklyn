package brooklyn.entity.java

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJava
import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag


public class VanillaJavaApp extends SoftwareProcessEntity implements UsesJava {

    // FIXME classpath values: need these to be downloaded and installed?
    
    private static final Logger log = LoggerFactory.getLogger(VanillaJavaApp.class)
    
    @SetFromFlag
    String main

    @SetFromFlag
    List<String> classpath

    @SetFromFlag
    List<String> args

    public VanillaJavaApp(Map props=[:], Entity owner=null) {
        super(props, owner)
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
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

    protected String getLogFileLocation() {
        return "$runDir/console"
    }
    
    @Override
    public void install() {
        // TODO install classpath entries?
        
        newScript(INSTALLING).
            failOnNonZeroResultCode()
            .execute();
    }

    @Override
    public void customize() {
        // no-op
    }
    
    @Override
    public void launch() {
        // TODO Use JAVA_OPTIONS config, once that is fixed to support more than sys properties
        // TODO quote args?
        String classpath = entity.classpath.join(" ")
        String clazz = entity.main
        String args = entity.args.join(" ")
        
        newScript(LAUNCHING, usePidFile:true).
            body.append(
                "java \$JAVA_OPTS -cp \"$classpath\" $clazz $args "+
                    " >> $runDir/console 2>&1 </dev/null &",
            ).execute();
    }
    
    @Override
    public boolean isRunning() {
        //TODO use PID instead
        newScript(CHECK_RUNNING, usePidFile: true)
                .execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(STOPPING, usePidFile: true)
                .execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return super.getCustomJavaConfigOptions() + ["-Xms128m", "-Xmx256m", "-XX:MaxPermSize=256m"]
    }
}
