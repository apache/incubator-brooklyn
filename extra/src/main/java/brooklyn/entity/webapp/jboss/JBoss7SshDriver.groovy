package brooklyn.entity.webapp.jboss

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.entity.webapp.JavaWebAppSshDriver
import brooklyn.entity.webapp.PortPreconditions
import brooklyn.entity.webapp.WebAppService
import brooklyn.location.basic.SshMachineLocation


class JBoss7SshDriver extends JavaWebAppSshDriver {

	/*
	 * TODO
	 * - port increment
	 * - use pid to stop
	 * - log _files_
	 * - collect ports used, release ports (http, management, jmx)
	 * 
	 * - apply interface to openshift, clusters
	 *   per server metrics
	 * 
	 * - more policies / examples
	 * 
	 * - refactor
	 * 		some of extra into library
	 * 		some of extra into extras
	 * 
	 * - brooklyn cloudfoundry extra
	 * - brooklyn whirr driver
	 * 
	 * - tiny example, localhost, with and without web console
	 * - medium example, web and data in AWS
	 * - big example, geo dns web+msg+data1+data2 , AWS, CloudSigma, Rackspace
	 */
	
    public static final int DEFAULT_FIRST_MANAGEMENT_PORT  = 9990
	    
    public static final String SERVER_TYPE = "standalone"
    private static final String BROOKLYN_JBOSS_CONFIG_FILENAME = "standalone-brooklyn.xml"
    
    public JBoss7SshDriver(JBoss7Server entity, SshMachineLocation machine) {
        super(entity, machine)
		entity.setAttribute(JBoss7Server.MANAGEMENT_PORT, managementPort)

    }

    @Override @Deprecated
	protected String getDefaultVersion() { return "7.0.0.Final" }
    
	protected String getLogFileLocation() { "${runDir}/${SERVER_TYPE}/log/server.log" }
	protected String getDeploySubdir() { "${SERVER_TYPE}/deployments" }
	
	protected int getManagementPort() { entity.getConfig(JBoss7Server.MANAGEMENT_PORT, DEFAULT_FIRST_MANAGEMENT_PORT) }
	
	@Override
	public void install() {
        String url = "http://download.jboss.org/jbossas/7.0/jboss-as-${version}/jboss-as-${version}.tar.gz"
        String saveAs  = "jboss-as-distribution-${version}.tar.gz"
		newScript(INSTALLING).
			failOnNonZeroResultCode().
			body.append(
				"curl -L \"${url}\" -o ${saveAs} || exit 9",
				"tar xzfv ${saveAs}",
			).execute();
	}

	// TODO: Too much sed! The last one is especially nasty.
	@Override
	public void customize() {
		PortPreconditions.checkPortsValid(httpPort:httpPort, managementPort:managementPort);
		newScript(CUSTOMIZING).
			body.append(
				"cp -r ${installDir}/jboss-as-${version}/${SERVER_TYPE} . || exit \$!",
				"cd ${runDir}/${SERVER_TYPE}/configuration/",
				"cp standalone.xml $BROOKLYN_JBOSS_CONFIG_FILENAME",
				"sed -i.bk 's/8080/${httpPort}/' $BROOKLYN_JBOSS_CONFIG_FILENAME",
				"sed -i.bk 's/9990/${managementPort}/' $BROOKLYN_JBOSS_CONFIG_FILENAME",
				
				//jmx not used; value -1 breaks it
//				"sed -i.bk 's/1090/${jmxPort}/' $brooklynConfig",  
				
				//disable the welcome root so we can deploy our own ROOT (not allowed otherwise!)
				"sed -i.bk 's/enable-welcome-root=\"true\"/enable-welcome-root=\"false\"/' $BROOKLYN_JBOSS_CONFIG_FILENAME",
				
				"sed -i.bk 's/inet-address value=\"127.0.0.1\"/any-address/' $BROOKLYN_JBOSS_CONFIG_FILENAME",
				"sed -i.bk 's/\\(path=\"deployments\"\\)/\\1 deployment-timeout=\"600\"/' $BROOKLYN_JBOSS_CONFIG_FILENAME"
			).execute();
		
		entity.deployInitialWars()
	}
	
	@Override
	public void launch() {
		newScript(LAUNCHING, usePidFile:true).
			body.append(
				"$installDir/jboss-as-${version}/bin/${SERVER_TYPE}.sh "+
					"--server-config $BROOKLYN_JBOSS_CONFIG_FILENAME "+
					"-Djboss.server.base.dir=$runDir/$SERVER_TYPE " + 
                	"\"-Djboss.server.base.url=file://$runDir/$SERVER_TYPE\" " +
					"-Djava.net.preferIPv4Stack=true "+
					"-Djava.net.preferIPv6Addresses=false " +
					" >> $runDir/console 2>&1 </dev/null &",
			).execute();
	}
	
	@Override
	public boolean isRunning() {
		//TODO use PID instead
		newScript(CHECK_RUNNING).
			body.append(
				"ps aux | grep '${entity.id}' | grep -v grep | grep -v ${SERVER_TYPE}.sh"
			).execute() == 0;
	}
	
	@Override
	public void stop() {
		newScript(STOPPING).
			body.append(
				"ps aux | grep '${entity.id}' | grep -v grep | awk '{ print \$2 }' | xargs kill -9"
			).execute();
	}

	@Override
	protected List<String> getCustomJavaConfigOptions() {
		return super.getCustomJavaConfigOptions() + ["-Xms200m", "-Xmx800m", "-XX:MaxPermSize=400m"]
	}

}
