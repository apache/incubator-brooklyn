package brooklyn.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicPortRange

// TODO OS-X failure, if no recent command line ssh
// ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
// Permission denied, please try again.
// ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
// Received disconnect from ::1: 2: Too many authentication failures for alex 

public abstract class SshBasedJavaAppSetup {
    static final Logger log = LoggerFactory.getLogger(SshBasedJavaAppSetup.class)
 
    public static final String DEFAULT_INSTALL_BASEDIR = "/tmp/brooklyn/installs/"
    public static final String DEFAULT_RUN_DIR = "/tmp/brooklyn/"
    public static final int DEFAULT_FIRST_JMX_PORT = 32199

    EntityLocal entity
    SshMachineLocation machine
    protected String appBaseDir
    protected String installDir
    protected String runDir;
    protected int jmxPort
    protected String jmxHost
    
    public SshBasedJavaAppSetup(EntityLocal entity, SshMachineLocation machine/*, Map properties=[:]*/) {
        this.entity = entity
        this.machine = machine
        jmxHost = machine.getAddress().getHostName();
    }
    
    public SshBasedJavaAppSetup setJmxPort(int val) {
        this.jmxPort = val
        return this
    }

    public SshBasedJavaAppSetup setJmxHost(String val) {
        this.jmxHost = val
        return this
    }

    public SshBasedJavaAppSetup setInstallDir(String val) {
        this.installDir = val
        return this
    }

    public SshBasedJavaAppSetup setRunDir(String val) {
        this.runDir = val
        return this
    }
    
    /** convenience to generate string -Dprop1=val1 -Dprop2=val2 for use with java */        
    public static String toJavaDefinesString(Map m) {
        StringBuffer sb = []
        m.each { key, value ->
	            sb.append("-D").append(key)
	            if (value!='') { sb.append('=\'').append(value).append('\'') }
	            sb.append(' ')
	        }
        return sb.toString().trim()
        //TODO - try the following instead
        //return m.collect( { "-D"+it.key+(it.value?:"='"+it.value+"'"} ).join(" ")
    }

    /**
     * Generates the valid range of possible ports. If desired is specified, then try to use exactly that.
     * Otherwise, use the range defaultFirst..65535. 
     */
    public static PortRange toDesiredPortRange(Integer desired, int defaultFirst) {
        if (desired == null | desired < 0) {
            return new BasicPortRange(defaultFirst, 65535)
        } else if (desired > 0) {
            return new BasicPortRange(desired, desired)
        } else if (desired == 0) {
            return BasicPortRange.ANY_HIGH_PORT
        }
    }

    protected Map getJvmStartupProperties() {
        [:] + getJmxConfigOptions()
    }
 
    /**
     * Return the JMX configuration properties used to start the service.
     * 
     * TODO security!
     */
    protected Map getJmxConfigOptions() {
        [
          'com.sun.management.jmxremote':'',
          'com.sun.management.jmxremote.port': jmxPort,
          'com.sun.management.jmxremote.ssl':false,
          'com.sun.management.jmxremote.authenticate':false,
          'java.rmi.server.hostname':jmxHost
        ]
    }
    
    protected List<String> makeInstallScript(List<String> lines) { 
        List<String> script = [
            "if [ -f $installDir/../INSTALL_COMPLETION_DATE ] ; then echo software is already installed ; exit ; fi",
			"mkdir -p $installDir",
			"cd $installDir/..",
        ]
        lines.each { script += it }
        script += "date > INSTALL_COMPLETION_DATE"
        return script
    }

    public List<String> getInstallScript() { Collections.emptyList() }
 
    public abstract List<String> getRunScript();
    
    public abstract Map<String, String> getRunEnvironment();
    
    /**
     * Should return script to run at remote server to determine whether process is running.
     * 
     * Script should return 0 if healthy, 1 if stopped, any other code if not healthy
     */
    public abstract List<String> getCheckRunningScript();
    
    /**
     * Installs the application on this machine, or no-op if no install-script defined.
     */
    public void install() {
        synchronized (getClass()) {
            List<String> script = getInstallScript()
            if (script) {
                log.info "installing entity {} on machine {}", entity, machine
                int result = machine.run(out:System.out, script)
                if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
            } else {
                log.debug "not installing entity {} on machine {}, as no install-script defined", entity, machine
            }
        }
    }
    
    public void runApp() {
        log.info "starting entity $entity on $machine, jmx $jmxHost:$jmxPort", entity, machine
        def result = machine.run(out:System.out, getRunScript(), getRunEnvironment())

        if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
        
        postStart();
    }

    public boolean isRunning() {
        def result = machine.run(out:System.out, getCheckRunningScript())
        if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
    }
    
    public void shutdown() {
        postShutdown();
    }
    
    @Deprecated // use explicit install/startApp steps
    public void start() {
        install();
        runApp();
    }
 
    /**
     * Called after start has completed, if successful. To be overridden; default is a no-op. 
     */
    protected void postStart() {
    }

    /**
     * Called after shutdown has completed, if successful. To be overridden; default is a no-op. 
     */
    protected void postShutdown() {
    }

    /**
     * Reserves a port (via machine.obtainPort). Uses the suggested port if greater than 0; if 0 then uses any high port; 
     * if less than 0 then uses defaultPort.
     * 
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
}
