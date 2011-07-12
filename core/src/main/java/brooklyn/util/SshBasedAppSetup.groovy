package brooklyn.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicPortRange
import brooklyn.util.internal.SshJschTool;

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

    public static final String DEFAULT_RUN_DIR = "/tmp/brooklyn"
    public static final String DEFAULT_INSTALL_BASEDIR = DEFAULT_RUN_DIR+"/"+"installs"

    EntityLocal entity
    SshMachineLocation machine

    protected String version
    protected String installDir
    protected String runDir

    public SshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity
        this.machine = machine
    }

	public SshBasedAppSetup setInstallDir(String val) {
        installDir = val
        return this
    }

    public SshBasedAppSetup setRunDir(String val) {
        runDir = val
        return this
    }

    public SshBasedAppSetup setVersion(String val) {
        version = val
        return this
    }

    /**
     * Add generic commands to an application specific installation script.
     *
     * The script will check for a {@code INSTALL_COMPLETION_DATE} file, and
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
            "[ -f $installDir/../INSTALL_COMPLETION_DATE ] && exit 0",
			"mkdir -p $installDir",
			"cd $installDir/..",
        ]
        lines.each { line -> script += "${line} || exit 1" }
        script += "date > INSTALL_COMPLETION_DATE"
        return script
    }

    /**
     * The script to run to on a remote machine to install the application.
     *
     * The default is a no-op.
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
     */
    public abstract List<String> getCheckRunningScript();

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
                int result = machine.run(out:System.out, script)
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
            List<String> script = getConfigScript()
            if (script) {
                log.info "Configuring entity {} on machine {}", entity, machine
                int result = machine.run(out:System.out, script)
                if (result) throw new IllegalStateException("failed to configure $entity (exit code $result)")
            } else {
                log.debug "not configuring entity {} on machine {}, as no config-script defined", entity, machine
            }
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
        def result = machine.run(out:System.out, getRunScript(), getRunEnvironment())

        if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
    }

    /**
     * Test whether the application is running.
     *
     * @see #getCheckRunningScript()
     */
    public boolean isRunning() {
        int result = machine.run(out:System.out, getCheckRunningScript())
        if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
    }

    /**
     * Shut down the application process.
     */
    public void shutdown() { }

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
     * Stop the Java application.
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
}
