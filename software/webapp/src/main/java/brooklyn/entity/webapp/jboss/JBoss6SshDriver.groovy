package brooklyn.entity.webapp.jboss

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.basic.lifecycle.ScriptHelper
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess
import brooklyn.entity.webapp.JavaWebAppSshDriver
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

import com.google.common.base.Preconditions


class JBoss6SshDriver extends JavaWebAppSshDriver {

	/*
	 * TODO
	 * - security for stats access (see below)
	 * - expose log file location, or even support accessing them dynamically
	 * - more configurability of config files, java memory, etc
	 */
	
    public static final String SERVER_TYPE = "standard"
    private static final String CONFIG_FILE = "standalone-brooklyn.xml"
	
	//NEW
//    public static final String DEFAULT_VERSION = "6.0.0.Final"
    public static final String DEFAULT_INSTALL_DIR = "$DEFAULT_INSTALL_BASEDIR/jboss"
    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final PORT_GROUP_NAME = "ports-brooklyn"
    
	//replicate JBoss6SShSetp.newInstance()
    public JBoss6SshDriver(JBoss6Server entity, SshMachineLocation machine) {
        super(entity, machine)
		
		
		Map<String,Map<String,String>> propFilesToGenerate = entity.getConfig(JBoss6Server.PROPERTY_FILES) ?: [:]
				
		log.info "Constructor of JBoss6SshDriver finished !!!"
	}

	protected String getLogFileLocation() { "${runDir}/server/$SERVER_TYPE/log/server.log" }
	protected String getDeploySubdir() { "deployments" } // FIXME what is this in as6?
	//protected Integer getManagementPort() { entity.getAttribute(JBoss6Server.MANAGEMENT_PORT) }
    //protected Integer getManagementNativePort() { entity.getAttribute(JBoss6Server.MANAGEMENT_NATIVE_PORT) }
    protected Integer getPortIncrement() { entity.getAttribute(JBoss6Server.PORT_INCREMENT) }
	protected String getClusterName() { entity.getAttribute(JBoss6Server.CLUSTER_NAME) }
	
	//replicate JBoss6SshSetp.getInstallScript()
	@Override
	public void install() {
		log.info "Starting install script ......................."
		String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent"
		String saveAs  = "jboss-as-distribution-${version}.tar.gz"
		log.info "url: $url"
		log.info "saveAs: $saveAs"
		// Note the -o option to unzip, to overwrite existing files without warning.
		// The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
		// overwrite interrupts the installer.
		newScript(INSTALLING).
			failOnNonZeroResultCode().
			body.append(
                CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs),
                CommonCommands.installExecutable("unzip"), 
				"unzip -o ${saveAs}",
			).execute();
		
		log.info "Install script finished ......................."
	}

    //?? replicates getConfigScript()
	@Override
	public void customize() {
		log.info "Starting customize script ............................"
		ScriptHelper sh = newScript(CUSTOMIZING).
			body.append(
			    "mkdir -p ${runDir}/server",
	            "cd ${runDir}/server",
	            "cp -r ${installDir}/jboss-${version}/server/${SERVER_TYPE} ${SERVER_TYPE}",
	            "cd ${SERVER_TYPE}/conf/bindingservice.beans/META-INF/",
	            "BJB=\"bindings-jboss-beans.xml\"",
	            "sed -i.bk 's/ports-03/${PORT_GROUP_NAME}/' \$BJB",
	            "sed -i.bk 's/<parameter>300<\\/parameter>/<parameter>${portIncrement}<\\/parameter>/' \$BJB",
			)
		sh.execute();
		
		entity.deployInitialWars()
		log.info "Just executed the script: ${sh.body.lines}"
		log.info "Finished customize script ............................"
	}
	
	//replicates getRunScript()
	@Override
	public void launch() {
		log.info "Starting launch script ............................"
		NetworkUtils.checkPortsValid(httpPort:httpPort, jmxPort:jmxPort);
		
		def clusterArg = (clusterName) ? "-g $clusterName" : ""
		// run.sh must be backgrounded otherwise the script will never return.
		ScriptHelper sh = newScript(LAUNCHING, usePidFile:false).
			body.append(
				"export LAUNCH_JBOSS_IN_BACKGROUND=1",
				"export RUN=${runDir}",
                "export JBOSS_CLASSPATH=${installDir}/jboss-${version}/lib/jboss-logmanager.jar",
				"${installDir}/jboss-${version}/bin/run.sh -Djboss.service.binding.set=${PORT_GROUP_NAME} -Djboss.server.base.dir=\$RUN/server " +
                    "-Djboss.server.base.url=file://\$RUN/server -Djboss.messaging.ServerPeerID=${entity.id} " +
                    "-b 0.0.0.0 ${clusterArg} -c ${SERVER_TYPE} " +
                    ">>\$RUN/console 2>&1 </dev/null &"
			)
		sh.execute();

		// TODO Where is the best place for this piece of code!?
		entity.setAttribute(JBoss6Server.HTTP_PORT, DEFAULT_HTTP_PORT+portIncrement)
		
		log.info "Just executed the script: ${sh.body.lines}"
		log.info "Finished launch script ............................"
	}
	
	//copied, used by isRunning()
	/** @see SshBasedJavaAppSetup#getCheckRunningScript() */
	private List<String> getCheckRunningScript() {
		def host = entity.getAttribute(Attributes.HOSTNAME)
		def port = entity.getAttribute(Attributes.JMX_PORT)
		List<String> script = [
			"${installDir}/jboss-${version}/bin/twiddle.sh --host ${host} --port ${port} get \"jboss.system:type=Server\" Started | grep false && exit 1"
		]
		return script
	}
	
	//replicates isRunning()
	@Override
	public boolean isRunning() {
		//have to override the CLI/JMX options
        int result = execute(getCheckRunningScript(), "checkRunning "+entity+" on "+machine, [:])
		log.info "Checking if JBoss is running: $result <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
		if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
	}
	
	//copied, used by stop()
    @Override
    private List<String> getShutdownScript() {
        def host = entity.getAttribute(Attributes.HOSTNAME)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> script = [
	            "${installDir}/jboss-${version}/bin/shutdown.sh --host ${host} --port ${port} -S",
            ]
        return script
    }
	
	//replicates shutdown() nad also includes postShutDown()
	@Override
	public void stop() {
		log.info "Starting stop script ............................"
	    //again, messy copy of parent; but new driver scheme could allow script-helper to customise parameters
	    log.debug "invoking shutdown script for {}: {}", entity, getShutdownScript()
	    def result = execute(getShutdownScript(), "shutdown "+entity+" on "+machine, [:])
	    if (result) log.warn "non-zero result code terminating {}: {}", entity, result
	    log.debug "done invoking shutdown script for {}", entity
		log.info "Finished stop script ............................"
	}

	@Override
	protected List<String> getCustomJavaConfigOptions() {
		return super.getCustomJavaConfigOptions() + ["-Xms200m", "-Xmx800m", "-XX:MaxPermSize=400m"]
	}
	
	//nothing for getCustomJavaSystemProperties()
	
	//??? nothing for getShellEnvironment()
}
