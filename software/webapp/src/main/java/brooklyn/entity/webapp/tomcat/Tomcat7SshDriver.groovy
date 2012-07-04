package brooklyn.entity.webapp.tomcat

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.basic.lifecycle.ScriptHelper
import brooklyn.entity.webapp.JavaWebAppSshDriver
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

import com.google.common.base.Preconditions


class Tomcat7SshDriver extends JavaWebAppSshDriver {

    public Tomcat7SshDriver(TomcatServer entity, SshMachineLocation machine) {
        super(entity, machine)
		Map<String,Map<String,String>> propFilesToGenerate = entity.getConfig(TomcatServer.PROPERTY_FILES) ?: [:]
	}

	protected String getLogFileLocation() { "${runDir}/logs/catalina.out" }
	protected String getDeploySubdir() { "webapps" }
    protected Integer getShutdownPort() { entity.getAttribute(TomcatServer.SHUTDOWN_PORT) ?: entity.getConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT) }
    
    @Override
    public void postLaunch(){
        entity.setAttribute(TomcatServer.SHUTDOWN_PORT, shutdownPort)
        entity.setAttribute(TomcatServer.TOMCAT_SHUTDOWN_PORT, shutdownPort)
        super.postLaunch()
    }
    
	@Override
	public void install() {
		String url = "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz"
		String saveAs  = "apache-tomcat-${version}.tar.gz"

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs));
        commands.add(CommonCommands.installExecutable("tar xvzf"));
        commands.add("tar xvzf ${saveAs}");

        newScript(INSTALLING).
			failOnNonZeroResultCode().
			body.append(commands).execute();
	}

	@Override
	public void customize() {
		newScript(CUSTOMIZING).
			body.append(
			    "mkdir -p ${runDir}",
	            "cd ${runDir}",
                "mkdir conf logs webapps temp",
                "cp ${installDir}/apache-tomcat-${version}/conf/{server,web}.xml conf/",
                "sed -i.bk s/8080/${httpPort}/g conf/server.xml",
                "sed -i.bk s/8005/${shutdownPort}/g conf/server.xml",
                "sed -i.bk /8009/D conf/server.xml"
			).execute();
		entity.deployInitialWars()
	}
	
	@Override
	public void launch() {
		NetworkUtils.checkPortsValid(httpPort:httpPort, jmxPort:jmxPort, shutdownPort:shutdownPort);
		newScript(LAUNCHING, usePidFile:false).
			body.append(
				"${installDir}/apache-tomcat-${version}/bin/startup.sh >>\$RUN/console 2>&1 </dev/null"
			).execute();
	}
	
	@Override
	public boolean isRunning() {
        return newScript(CHECK_RUNNING, usePidFile:"pid.txt").execute() == 0
	}
	
	@Override
	public void stop() {
        newScript(STOPPING, usePidFile:"pid.txt").execute()
	}

	@Override
	protected List<String> getCustomJavaConfigOptions() {
		return super.getCustomJavaConfigOptions() + ["-Xms200m", "-Xmx800m", "-XX:MaxPermSize=400m"]
	}
    
    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> superResult = super.getShellEnvironment()
        superResult +
        [
                "CATALINA_BASE" : "${runDir}",
                "CATALINA_OPTS" : superResult.JAVA_OPTS,
                "CATALINA_PID" : "pid.txt",
                "RUN" : "${runDir}",
        ]
    }
    
    @Override
    protected Map getCustomJavaSystemProperties() {
        // FIXME Anything needed?
        return super.getCustomJavaSystemProperties()
    }
	
}
