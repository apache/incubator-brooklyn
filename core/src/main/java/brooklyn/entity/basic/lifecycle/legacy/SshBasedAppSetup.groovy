package brooklyn.entity.basic.lifecycle.legacy

import java.io.File
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.ScriptHelper
import brooklyn.entity.basic.lifecycle.ScriptRunner
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.PortRanges.BasicPortRange

import com.google.common.base.Strings

/**
 * Application installation, configuration and startup using ssh.
 *
 * TODO complete documentation
 *
 * @see SshJschTool
 * @see SshMachineLocation
 */

//FIXME ALEX rename SshBasedSoftwareSetup
public abstract class SshBasedAppSetup extends StartStopSshDriver implements ScriptRunner {
    protected static final Logger log = LoggerFactory.getLogger(SshBasedAppSetup.class)

	public void setVersion(String s) {
		if (getVersion()!=s) log.warn("legacy api, unsupported setting for $entity: version $s (is ${getVersion()})")
	}
//	public void setInstallDir(String s) {
//		if (getInstallDir()!=s) log.warn("unsupported setting $s (is ${getInstallDir()})", new Throwable())
//	}
	public void setRunDir(String s) {
		if (getRunDir()!=s) log.warn("legacy api, unsupported setting for $entity: run dir $s (is ${getRunDir()}")
	}

	//for compatibility with legacy entities
	String manualInstallDir = null;
	protected String setInstallDir(String val) {
		manualInstallDir = val
	}
	@Override
	protected String getInstallDir() {
		manualInstallDir ?: super.getInstallDir()
	}
	
	protected String getDefaultVersion() { 
		def result = super.getDefaultVersion();
		if (result==NO_VERSION_INFO && hasProperty("DEFAULT_VERSION"))
			return getProperty("DEFAULT_VERSION");
		return result; 
	}

    String deployDir

    public SshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
		super(entity, machine)
    }
		
	protected void setEntityAttributes() {
		entity.setAttribute(Attributes.VERSION, version)
	}
	
	// old style script...
	protected ScriptHelper newScript(Map flags=[:], String phase) {
		def s = new ScriptHelper(this, phase+" "+this);
		if (phase==INSTALLING) {
			s.header.append(
				'export INSTALL_DIR="'+installDir+'"',
				'test -f $INSTALL_DIR/../BROOKLYN && exit 0',
				'mkdir -p $INSTALL_DIR',
				'cd $INSTALL_DIR/..'
			).footer.append(
				'date > $INSTALL_DIR/../BROOKLYN'
			)
		}
		if (phase in [CUSTOMIZING, LAUNCHING, CHECK_RUNNING, STOPPING]) {
			s.header.append(
				"export RUN_DIR=\"${runDir}\"",
				'mkdir -p $RUN_DIR',
				'cd $RUN_DIR'
			)
		}
		
		if (phase in [CUSTOMIZING])
			s.skipIfBodyEmpty()
		if (phase in [CHECK_RUNNING, LAUNCHING, STOPPING])
			s.failIfBodyEmpty()

		if (flags.usePidFile) {
			String pidFile = (flags.usePidFile in String ? flags.usePidFile : "${runDir}/pid.txt")
			if (phase in [LAUNCHING])
				s.footer.prepend("cat \$! > ${pidFile}")
			else if (phase in [CHECK_RUNNING])
				s.body.append(
					"test -f ${pidFile} || exit 1", //no pid, not running
					
					//old method, for supplied service, or entity.id
//					"ps aux | grep ${service} | grep \$(cat ${pidFile}) > /dev/null"
					//new way, preferred?
					"ps -p `cat ${pidFile}`",
					
				).requireResultCode { it==0 || it==1 }
				// 1 is not running
				
			else if (phase in [STOPPING])
				s.body.append(
					"export PID=`cat ${pidFile}`",
					'[[ -n "$PID" ]] || exit 0',
					'kill $PID',
					'kill -9 $PID',
					"rm ${pidFile}"
				)
			else
				log.warn("usePidFile script option not valid for "+s.summary)
		}
		
		return s
	}
	
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
		newScript(INSTALLING).body.append(lines).lines
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
     * The {@link #getShellEnvironment()} should be used to set any environment
     * variables required.
     *
     * @return a {@link List} of shell commands
     *
     * @see #getShellEnvironment()
     */
    public abstract List<String> getRunScript();

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
			//FIXME
			// ps -p instead?
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
                int result = execute(script, "install "+entity+" on "+machine)
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

            List<String> script = getConfigScript()
            if (script) {
                log.info "configuring entity {} on machine {}", entity, machine
                int result = execute(script, "config "+entity+" on "+machine)
                if (result) throw new IllegalStateException("failed to configure $entity (exit code $result)")
            } else {
                log.debug "not configuring entity {} on machine {}, as no config-script defined", entity, machine
            }
        }
    }

    protected int exec(List<String> script, String summaryForLogging="execute for", boolean terminateOnExit) {
        synchronized (entity) {
			int result = execute(script, summaryForLogging);
            if (terminateOnExit && result) 
				throw new IllegalStateException("failed to "+summaryForLogging+" $entity (exit code $result)")
			result;
        }
    }

    /**
     * Run the application on this machine.
     *
     * The SHELL_ENVIRONMENT env property a {@link Map} of
     * environment variables and their values which will be set before executing
     * the commands in {@link #getRunScript()}.
     *
     * @see #start()
     * @see #getRunScript()
     */
    public void runApp() {
        log.info "starting {} on {}", entity, machine
        Map environment = [:]
        environment << getShellEnvironment()
        Map configured = entity.getConfig(SoftwareProcessEntity.SHELL_ENVIRONMENT)
        configured.each { key, value ->
            if (value in Closure) {
                environment.put(key, ((Closure) value).call())
            } else {
                environment.put(key, value)
            }
        }
        def result = execute(getRunScript(), "runApp "+entity+" on "+machine, environment)

        if (result) throw new IllegalStateException("failed to start $entity (exit code $result)")
    }

    /**
     * Test whether the application is running.
     *
     * @see #getCheckRunningScript()
     */
    public boolean isRunning() {
        int result = execute(getCheckRunningScript(), "checkRunning "+entity+" on "+machine)
        if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
    }

    /**
     * Shut down the application process.
     */
    public void shutdown() {
        log.debug "invoking shutdown script for {}: {}", entity, getShutdownScript()
        def result = execute(getShutdownScript(), "shutdown "+entity+" on "+machine)
        if (result) log.warn "non-zero result code terminating {}: {}", entity, result
        log.debug "done invoking shutdown script for {}", entity
    }

	public void customize() {
		config();
	}
	public void launch() {
		runApp();
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
        log.info "stopped {}", entity
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
        } else {
	        log.debug "invoking restart script on {}: {}", entity, restartScript
	        def result = execute(getRestartScript(), "restart "+entity+" on "+machine)
	        if (result) log.info "non-zero result code terminating {}: {}", entity, result
	        log.debug "done invoking restart script on {}", entity
        }
    }

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
    @Deprecated
    public static PortRange toDesiredPortRange(Integer desired, Integer defaultFirst=desired) {
        if (desired == null || desired < 0) {
            return PortRanges.fromString(defaultFirst+"+");
        } else if (desired > 0) {
            return PortRanges.fromInteger(desired);
        } else if (desired == 0) {
            return PortRanges.ANY_HIGH_PORT
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
    @Deprecated
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
    @Deprecated
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
        log.info "deploying file {} to {} on {}", file.name, target, machine
        try {
            machine.copyTo file, target
        } catch (Exception ioe) {
            log.error "Failed to copy {} to {} (rethrowing): {}", file.name, machine, ioe.message
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
                log.debug "deployed {} on {}", local.name, machine
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
		String t1 = (target!=null? target : new File(deployDir, server.name))
        List<String> script = [
            "test -f ${server} || exit 1",
            "cp ${server} ${t1}",
        ]
		if (target==null) {
			// FIXME this is a hack, currently deploys as ROOT.war also, for anything which has no name specified
			// root wars are handled correctly in the brooklyn JBoss7 class hierarchy
			t1 = new File(deployDir, "ROOT.war")
			script << "cp ${server} ${t1}"
		}
        return script
    }
}
