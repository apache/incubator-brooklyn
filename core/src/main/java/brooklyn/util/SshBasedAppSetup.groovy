package brooklyn.util

import java.io.File;
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.location.PortRange
import brooklyn.location.basic.BasicPortRange
import brooklyn.location.basic.SshMachineLocation

import com.google.common.base.Strings

/**
 * Application installation, configuration and startup using ssh.
 *
 * TODO complete documentation
 *
 * @see SshJschTool
 * @see SshMachineLocation
 */
public abstract class SshBasedAppSetup {
    protected static final Logger log = LoggerFactory.getLogger(SshBasedAppSetup.class)

    public static final String BROOKLYN_HOME_DIR = "/tmp/brooklyn"
    public static final String DEFAULT_INSTALL_BASEDIR = BROOKLYN_HOME_DIR+"/"+"installs"

    EntityLocal entity
    SshMachineLocation machine

    protected String version
    protected String installDir
    protected String runDir
    protected String deployDir

    public SshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity
        this.machine = machine
    }

	public void setInstallDir(String val) {
        installDir = val
    }

    public void setRunDir(String val) {
        runDir = val
    }

    public void setDeployDir(String val) {
        deployDir = val
    }

    public void setVersion(String val) {
        version = val
    }

    protected void setEntityAttributes() {
        entity.setAttribute(Attributes.VERSION, version)
    }

    protected void setCustomAttributes() { }

    /**
     * Add generic commands to an application specific installation script.
     *
     * The script will check for a {@code BROOKLYN} file, and
     * skip the installation if it exists, otherwise it executes the commands
     * to install the applications and creates the file with the current
     * date and time.
     * <p>
     * The script will exit with status 0 on success and 1 on failure.
     *
     * @see #getInstallScript()
     */
    protected List<String> makeInstallScript(List<String> lines) {
        if (lines.isEmpty()) return lines
        List<String> script = [
            "export INSTALL=\"${installDir}\"",
            "test -f \$INSTALL/../BROOKLYN && exit 0",
			"mkdir -p \$INSTALL",
			"cd \$INSTALL/..",
        ]
        lines.each { line -> script += "${line}" }
        script += "date > \$INSTALL/../BROOKLYN"
        return script
    }

    /**
     * The script to run to on a remote machine to install the application.
     *
     * The default is a no-op. The shell variable {@code INSTALL} is exported with
     * the path to the installation directory for the application.
     *
     * @return a {@link List} of shell commands
     */
    public List<String> getInstallScript() { Collections.emptyList() }

    /**
     * The script to run to on a remote machine to configure the application.
     *
     * The default is a no-op.
     *
     * @return a {@link List} of shell commands
     */
    public List<String> getConfigScript() { Collections.emptyList() }

    /**
     * The script to run to on a remote machine to run the application.
     *
     * The {@link #getRunEnvironment()} should be used to set any environment
     * variables required.
     *
     * @return a {@link List} of shell commands
     *
     * @see #getRunEnvironment()
     */
    public abstract List<String> getRunScript();

    /**
     * The environment variables to be set when executing the commands to run
     * the application.
     *
     * @see #getRunScript()
     */
    public abstract Map<String, String> getRunEnvironment();

    /**
     * The script to run to on a remote machine to determine whether the
     * application is running.
     *
     * The script should exit with status 0 if healthy, 1 if stopped, any other
     * code if not healthy.
     *
     * @return a {@link List} of shell commands
     *
     * @see #isRunning()
     * @see #makeCheckRunningScript(String, String)
     */
    public abstract List<String> getCheckRunningScript();

    /**
     * Default commands for {@link #getCheckRunningScript()}.
     *
     * This method will generate script commands to check for the presence of a PID
     * file with a given name and a process matching the PID with the correct
     * service name or entity id (if the service is not given). This script
     * also forms the basis of the generated restart and shutdown scripts.
     *
     * @see #getCheckRunningScript()
     * @see #makeRestartScript(String)
     * @see #makeShutdownScript(String)
     */
    protected List<String> makeCheckRunningScript(String service = null, String pidFile = "pid.txt") {
        if (Strings.isNullOrEmpty(service)) service = entity.id
        List<String> script = [
            "cd ${runDir}",
            "test -f ${pidFile}",
            "ps aux | grep ${service} | grep \$(cat ${pidFile}) > /dev/null"
        ]
        return script
    }

    protected List<String> makeCheckPortOpenScript(int port) {
        List<String> script = [
            "lsof -t -i TCP:$port"
        ]
        return script
    }

    /**
     * The script to run to on a remote machine to restart the application.
     *
     * @return a {@link List} of shell commands
     */
    public List<String> getRestartScript() { makeRestartScript() }

    /** @see SshBasedJavaSetup#getRestartScript() */
    protected List<String> makeRestartScript(String service = null, String pidFile = "pid.txt") {
        return makeCheckRunningScript(service, pidFile) + [ "kill -HUP \$(cat ${pidFile})" ]
    }

    /**
     * The script to run to on a remote machine to shutdown the application.
     *
     * @return a {@link List} of shell commands
     */
    public List<String> getShutdownScript() { makeShutdownScript() }

    /** @see SshBasedJavaSetup#getShutdownScript() */
    protected List<String> makeShutdownScript(String service = null, String pidFile = "pid.txt") {
        return makeCheckRunningScript(service, pidFile) + [
            "kill \$(cat ${pidFile})",
            "sleep 1",
            "kill -9 \$(cat ${pidFile}) || exit 0",
        ]
    }

    /**
     * Installs the application on this machine, or no-op if no install-script defined.
     *
     * @see #getInstallScript()
     */
    public void install() {
        synchronized (getClass()) {
            List<String> script = getInstallScript()
            if (script) {
                log.info "installing entity {} on machine {}", entity, machine
                int result = machine.run(out:System.out, err:System.err, script)
                if (result) throw new IllegalStateException("failed to install $entity (exit code $result)")
            } else {
                log.debug "not installing entity {} on machine {}, as no install-script defined", entity, machine
            }
        }
    }

    /**
     * Configure the application on this machine, or no-op if no config-script defined.
     *
     * @see #getConfigScript()
     */
    public void config() {
        synchronized (entity) {
            setEntityAttributes()
            setCustomAttributes()

            List<String> script = getConfigScript()
            if (script) {
                log.info "Configuring entity {} on machine {}", entity, machine
                int result = machine.run(out:System.out, err:System.err, script)
                if (result) throw new IllegalStateException("failed to configure $entity (exit code $result)")
            } else {
                log.debug "not configuring entity {} on machine {}, as no config-script defined", entity, machine
            }
        }
    }

    protected void exec(List<String> script, String summaryForLogging="execute for") {
        synchronized (entity) {
            log.info(summaryForLogging+" entity {} on machine {}", entity, machine)
            int result = machine.run(out:System.out, err:System.err, script)
            if (result) throw new IllegalStateException("failed to "+summaryForLogging+" $entity (exit code $result)")
        }
    }

    /**
     * Run the application on this machine.
     *
     * The {@link #getRunEnvironment()} method should return a {@link Map} of
     * environment variables and their values which will be set before executing
     * the commands in {@link #getRunScript()}.
     *
     * @see #start()
     * @see #getRunScript()
     * @see #getRunEnvironment()
     */
    public void runApp() {
        log.info "starting entity {} on {}", entity, machine
        Map environment = [:]
        environment << getRunEnvironment()
        Map configured = entity.getConfig(AbstractService.ENVIRONMENT)
        configured.each { key, value ->
            if (value in Closure) {
                environment.put(key, ((Closure) value).call())
            } else {
                environment.put(key, value)
            }
        }
        def result = machine.run(out:System.out, err:System.err, getRunScript(), environment)

        if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
    }

    /**
     * Test whether the application is running.
     *
     * @see #getCheckRunningScript()
     */
    public boolean isRunning() {
        int result = machine.run(out:System.out, err:System.err, getCheckRunningScript())
        if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
    }

    /**
     * Shut down the application process.
     */
    public void shutdown() {
        log.debug "invoking shutdown script"
        def result = machine.run(out:System.out, err:System.err, getShutdownScript())
        if (result) log.info "non-zero result code terminating {}: {}", entity, result
        log.debug "done invoking shutdown script"
    }

    /**
     * Start the application.
     *
     * this installs, configures and starts the application process. However,
     * users can also call the {@link #install()}, {@link #config()} and
     * {@link #runApp()} steps independently. The {@link #postStart()} method
     * will be called after the application run script has been executed, but
     * the process may not be completely initialised at this stage, so care is
     * required when implementing these stages.
     *
     * @see #stop()
     */
    public void start() {
        install()
        config()
        runApp()
        postStart()
    }

    /**
     * Stop the application.
     *
     * May also use the explicit {@link #shutdown()} step, however this call
     * also executes the {@link #postShutdown()} method if successful.
     *
     * @see #start()
     */
    public void stop() {
        shutdown()
        postShutdown()
    }

    /**
     * Restart the application.
     * 
     * If {@link #getRestartScript()} is empty, this will simply stop and then start the service, otherwise
     * the script will be run.
     *
     * @see #start()
     * @see #stop()
     */
    public void restart() {
        if (restartScript.isEmpty()) {
	        stop()
	        runApp()
	        postStart()
        } else {
	        log.debug "invoking restart script"
	        def result = machine.run(out:System.out, err:System.err, restartScript)
	        if (result) log.info "non-zero result code terminating {}: {}", entity, result
	        log.debug "done invoking restart script"
        
        }
    }

    /**
     * Called when starting the application, after the run step has completed
     * without an exception.
     *
     * To be overridden; default is a no-op.
     *
     * @see #start()
     * @see #runApp()
     */
    protected void postStart() { }

    /**
     * Called when stopping the application, if the shutdown step completes
     * without an exception.
     *
     * To be overridden; default is a no-op.
     *
     * @see #stop()
     * @see #shutdown()
     */
    protected void postShutdown() { }

    /**
     * Generates a valid range of possible ports.
     *
     * If desired is specified, then try to use exactly that. Otherwise, use the
     * range from defaultFirst to 65535.
     */
    public static PortRange toDesiredPortRange(Integer desired, Integer defaultFirst=desired) {
        if (desired == null || desired < 0) {
            return new BasicPortRange(defaultFirst, 65535)
        } else if (desired > 0) {
            return new BasicPortRange(desired, desired)
        } else if (desired == 0) {
            return BasicPortRange.ANY_HIGH_PORT
        }
    }

    /**
     * Reserves a port.
     *
     * Uses the suggested port if greater than 0; if 0 then uses any high port;
     * if less than 0 then uses defaultPort. If canIncrement is true, will reserve
     * a port in the range between the suggested value and 65535.
     *
     * TODO support a flag for privileged ports less than 1024
     *
     * @return the reserved port number
     *
     * @see #obtainPort(int, boolean)
     * @see SshMachineLocation#obtainPort(PortRange)
     */
    protected int obtainPort(int suggested, int defaultPort, boolean canIncrement) {
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

    /** @see #obtainPort(int, int, boolean) */
    protected int obtainPort(int suggested, boolean canIncrement) {
        if (suggested < 0) throw new IllegalArgumentException("Port $suggested must be >= 0")
        obtainPort(suggested, suggested, canIncrement)
    }

    /**
     * Copy a file to the {@link #runDir} on the server.
     *
     * @return The location of the file on the server
     */
    public File copy(File file) {
        File target = new File(runDir, file.name)
        log.info "Deploying file {} to {} on {}", file.name, target, machine
        try {
            machine.copyTo file, target
        } catch (IOException ioe) {
            log.error "Failed to copy {} to {}: {}", file.name, machine, ioe.message
            throw new IllegalStateException("Failed to copy ${file.name} to ${machine}", ioe)
        }
        return target
    }

    /**
     * Copies a file to the server and invokes {@link #getDeployScript(String)}
     * for further processing.
     */
    public void deploy(File local, File remote=null) {
        File server = copy(local)
        List<String> deployScript = getDeployScript(server, remote)
        if (deployScript && !deployScript.isEmpty()) {
            int result = machine.run(out:System.out, deployScript)
            if (result != 0) {
                log.error "Failed to deploy {} on {}, result {}", local.name, machine, result
                throw new IllegalStateException("Failed to deploy ${local.name} on ${machine}")
            } else {
                log.debug "Deployed {} on {}", local.name, machine
            }
        }
    }

    /**
     * Deploy the file found at the specified location on the server.
     *
     * Checks that the file exists, and fails if not accessible, otherwise copies it
     * to the configured deploy directory. This is required because exit status from
     * the Jsch scp command is not reliable.
     */
    public List<String> getDeployScript(File server, File target=null) {
        if (target == null) {
            target = new File(deployDir, server.name)
        }
        List<String> script = [
            "test -f ${server} || exit 1",
            "cp ${server} ${target}",
        ]
        return script
    }
}
