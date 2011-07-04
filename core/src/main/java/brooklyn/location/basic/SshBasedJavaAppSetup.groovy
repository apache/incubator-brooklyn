package brooklyn.location.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.PortRange

// TODO OS-X failure, if no recent command line ssh
// ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
// Permission denied, please try again.
// ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
// Received disconnect from ::1: 2: Too many authentication failures for alex 

public abstract class SshBasedJavaAppSetup {
    static final Logger log = LoggerFactory.getLogger(SshBasedJavaAppSetup.class)
 
    EntityLocal entity
    SshMachineLocation machine
    String appBaseDir
    String brooklynBaseDir = "/tmp/brooklyn"
    String installsBaseDir = brooklynBaseDir+"/installs"
    int jmxPort
    String jmxHost
    
    public SshBasedJavaAppSetup(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity
        this.machine = machine
        appBaseDir = brooklynBaseDir + "/" + "app-"+entity.getApplication()?.id
        
        log.debug "setting jmxHost on $entity as {}", machine
        jmxHost = machine.address.hostName
        jmxPort = obtainPort(32199, true);
    }

    /** convenience to generate string -Dprop1=val1 -Dprop2=val2 for use with java */        
    public static String toJavaDefinesString(Map m) {
        StringBuffer sb = []
        m.each { sb.append("-D"); sb.append(it.key); if (it.value!='') { sb.append('=\''); sb.append(it.value); sb.append('\' ') } else { sb.append(' ') } }
        return sb.toString().trim()
        //TODO - try the following instead
        //return m.collect( { "-D"+it.key+(it.value?:"='"+it.value+"'"} ).join(" ")
    }

    /**
    * Uses suggested port if greater than 0; if 0 then uses any high port; if less than 0 then uses defaultPort.
    * @param suggested
    * @param defaultPort
    * @param canIncrement
    * @return
    */
    protected int obtainPort(Integer suggested, int defaultPort, boolean canIncrement) {
        PortRange range;
        if (suggested > 0) {
            range = (canIncrement) ? new BasicPortRange(suggested, 65535) : new BasicPortRange(suggested, suggested)
        } else if (suggested == 0) {
            range = BasicPortRange.ANY_HIGH_PORT
        } else {
            range = (canIncrement) ? new BasicPortRange(defaultPort, 65535) : new BasicPortRange(defaultPort, defaultPort)
        }
        return machine.obtainPort(range);
    }

    protected int obtainPort(int suggested, boolean canIncrement) {
        if (suggested < 0) throw new IllegalArgumentException("Port $suggested must be >= 0")
        obtainPort(suggested, suggested, canIncrement)
    }
    
    public Map getJvmStartupProperties() {
        [:] + getJmxConfigOptions()
    }
 
    /**
     * Return the JMX configuration properties used to start the service.
     * 
     * TODO security!
     */
    public Map getJmxConfigOptions() {
        [
          'com.sun.management.jmxremote':'',
          'com.sun.management.jmxremote.port': jmxPort,
          'com.sun.management.jmxremote.ssl':false,
          'com.sun.management.jmxremote.authenticate':false,
          'java.rmi.server.hostname':jmxHost
        ]
    }
    
    protected String makeInstallScript(String ...lines) { 
        String result = """\
if [ -f $installDir/../INSTALL_COMPLETION_DATE ] ; then echo software is already installed ; exit ; fi
mkdir -p $installDir && \\
cd $installDir/.. && \\
""";
        lines.each { result += it + "&& \\\n" }
        // TODO use .collect, as above
        result += """\
date > INSTALL_COMPLETION_DATE
exit
""" 
    }

    public String getInstallScript() { null }
 
    public abstract String getRunScript();
    
    /**
     * Should return script to run at remote server to determine whether process is running.
     * 
     * Script should return 0 if healthy, 1 if stopped, any other code if not healthy
     */
    public abstract String getCheckRunningScript();
    
    public void start() {
        log.info "starting entity {} on machine {}", entity, machine
        synchronized (getClass()) {
            String s = getInstallScript()
            if (s) {
                int result = machine.run(out:System.out, s)
                if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
            }
        }

        def result = machine.run(out:System.out, getRunScript())
        if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
        
        postStart();
    }
 
    /**
     * Called after start has completed, if successful. To be overridden; default is a no-op. 
     */
    protected void postStart() {
    }

    protected void postShutdown() {
        machine.releasePort(jmxPort)
    }

    public boolean isRunning() {
        def result = machine.run(out:System.out, getCheckRunningScript())
        if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
    }
    
    public abstract void shutdown();
}
