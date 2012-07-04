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
	
    public static final String SERVER_TYPE = "standard"
    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final PORT_GROUP_NAME = "ports-brooklyn"
    
    public JBoss6SshDriver(JBoss6Server entity, SshMachineLocation machine) {
        super(entity, machine)
		Map<String,Map<String,String>> propFilesToGenerate = entity.getConfig(JBoss6Server.PROPERTY_FILES) ?: [:]
	}

	protected String getLogFileLocation() { "${runDir}/server/$SERVER_TYPE/log/server.log" }
	protected String getDeploySubdir() { "server/${SERVER_TYPE}/deploy" } // FIXME what is this in as6?
    protected Integer getPortIncrement() { entity.getAttribute(JBoss6Server.PORT_INCREMENT) }
	protected String getClusterName() { entity.getAttribute(JBoss6Server.CLUSTER_NAME) }
	
    @Override
    public void postLaunch(){
        entity.setAttribute(JBoss6Server.HTTP_PORT, DEFAULT_HTTP_PORT+portIncrement)
        super.postLaunch()
    }
    
	@Override
	public void install() {
		String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent"
		String saveAs  = "jboss-as-distribution-${version}.tar.gz"
		// Note the -o option to unzip, to overwrite existing files without warning.
		// The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
		// overwrite interrupts the installer.

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs));
        commands.add(CommonCommands.installExecutable("unzip"));
        commands.add("unzip -o ${saveAs}");

        newScript(INSTALLING).
			failOnNonZeroResultCode().
			body.append(commands).execute();
	}

	@Override
	public void customize() {
		newScript(CUSTOMIZING).
			body.append(
			    "mkdir -p ${runDir}/server",
	            "cd ${runDir}/server",
	            "cp -r ${installDir}/jboss-${version}/server/${SERVER_TYPE} ${SERVER_TYPE}",
	            "cd ${SERVER_TYPE}/conf/bindingservice.beans/META-INF/",
	            "BJB=\"bindings-jboss-beans.xml\"",
	            "sed -i.bk 's/ports-03/${PORT_GROUP_NAME}/' \$BJB",
	            "sed -i.bk 's/<parameter>300<\\/parameter>/<parameter>${portIncrement}<\\/parameter>/' \$BJB",
			).execute();
		entity.deployInitialWars()
	}
	
	@Override
	public void launch() {
		NetworkUtils.checkPortsValid(httpPort:httpPort, jmxPort:jmxPort);	
		def clusterArg = (clusterName) ? "-g $clusterName" : ""
		// run.sh must be backgrounded otherwise the script will never return.
		newScript(LAUNCHING, usePidFile:false).
			body.append(
                "export JBOSS_CLASSPATH=${installDir}/jboss-${version}/lib/jboss-logmanager.jar",
				"${installDir}/jboss-${version}/bin/run.sh -Djboss.service.binding.set=${PORT_GROUP_NAME} -Djboss.server.base.dir=\$RUN/server " +
                    "-Djboss.server.base.url=file://\$RUN/server -Djboss.messaging.ServerPeerID=${entity.id} " +
                    "-Djboss.boot.server.log.dir=${runDir}/server/${SERVER_TYPE}/log " +
                    "-b 0.0.0.0 ${clusterArg} -c ${SERVER_TYPE} " +
                    ">>\$RUN/console 2>&1 </dev/null &"
			).execute();
	}
	
	@Override
	public boolean isRunning() {
        def host = entity.getAttribute(Attributes.HOSTNAME)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> checkRunningScript = [
            "${installDir}/jboss-${version}/bin/twiddle.sh --host ${host} --port ${port} get \"jboss.system:type=Server\" Started | grep false && exit 1"
        ]
		//have to override the CLI/JMX options
        int result = execute(checkRunningScript, "checkRunning "+entity+" on "+machine, env:[:])
		if (result==0) return true
        if (result==1) return false
        throw new IllegalStateException("$entity running check gave result code $result")
	}
	
	@Override
	public void stop() {
        def host = entity.getAttribute(Attributes.HOSTNAME)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> shutdownScript = [
            "${installDir}/jboss-${version}/bin/shutdown.sh --host ${host} --port ${port} -S",
        ]
	    //again, messy copy of parent; but new driver scheme could allow script-helper to customise parameters
	    log.debug "invoking shutdown script for {}: {}", entity, shutdownScript
	    def result = execute(shutdownScript, "shutdown "+entity+" on "+machine, env:[:])
	    if (result) log.warn "non-zero result code terminating {}: {}", entity, result
	    log.debug "done invoking shutdown script for {}", entity
	}

	@Override
	protected List<String> getCustomJavaConfigOptions() {
		return super.getCustomJavaConfigOptions() + ["-Xms200m", "-Xmx800m", "-XX:MaxPermSize=400m"]
	}
    
    @Override
    public Map<String, String> getShellEnvironment() {
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        super.getShellEnvironment() +
        [
            "LAUNCH_JBOSS_IN_BACKGROUND" : "1",
            "RUN" : "${runDir}",
        ]
    }
    
    @Override
    protected Map getCustomJavaSystemProperties() {
        Map<String, String> options = [
            "jboss.platform.mbeanserver" : null,
            "javax.management.builder.initial" : "org.jboss.system.server.jmx.MBeanServerBuilderImpl",
            "java.util.logging.manager" : "org.jboss.logmanager.LogManager",
            "org.jboss.logging.Logger.pluginClass" : "org.jboss.logging.logmanager.LoggerPluginImpl"
        ]
        return options
    }
	
}
