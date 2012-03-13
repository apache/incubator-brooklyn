package brooklyn.entity.webapp.jboss

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.entity.webapp.JavaWebAppSshDriver
import brooklyn.entity.webapp.PortPreconditions
import brooklyn.entity.webapp.WebAppService
import brooklyn.location.PortRange;
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
	
    public static final String SERVER_TYPE = "standalone"
    private static final String CONFIG_FILE = "standalone-brooklyn.xml"
    
    public JBoss7SshDriver(JBoss7Server entity, SshMachineLocation machine) {
        super(entity, machine)
    }

	protected String getLogFileLocation() { "${runDir}/${SERVER_TYPE}/log/server.log" }
	protected String getDeploySubdir() { "${SERVER_TYPE}/deployments" }
	protected Integer getManagementPort() { entity.getAttribute(JBoss7Server.MANAGEMENT_PORT) }
	
	@Override
	public void install() {
        String url = "http://download.jboss.org/jbossas/7.1/jboss-as-${version}/jboss-as-${version}.tar.gz"
        String saveAs  = "jboss-as-distribution-${version}.tar.gz"
		newScript(INSTALLING).
			failOnNonZeroResultCode().
			body.append(
				"curl -L \"${url}\" -o ${saveAs} || exit 9",
				"tar xzfv ${saveAs}",
			).execute();
	}

    /**
     * AS7 config notes and TODOs: 
     *  We're using the http management interface on port managementPort
     *  We're not using any JMX.
     *   - AS 7 simply doesn't boot with Sun JMX enabled (https://issues.jboss.org/browse/JBAS-7427)
     *   - 7.1 onwards uses Remoting 3, which we haven't configured
     *  We're completely disabling security on the management interface.
     *   - In the future we probably want to use the as7/bin/add-user.sh script using config keys for user and password
     *   - Or we could create our own security realm and use that.
     *  We disable the root welcome page, since we can't deploy our own root otherwise
     *  
     */
	@Override
	public void customize() {
		PortPreconditions.checkPortsValid(httpPort:httpPort, managementPort:managementPort);
		newScript(CUSTOMIZING).
			body.append(
				"cp -r ${installDir}/jboss-as-${version}/${SERVER_TYPE} . || exit \$!",
				"cd ${runDir}/${SERVER_TYPE}/configuration/",
				"cp standalone.xml $CONFIG_FILE",
				"sed -i.bk 's/8080/${httpPort}/' $CONFIG_FILE",
				"sed -i.bk 's/9990/${managementPort}/' $CONFIG_FILE",
                "sed -i.bk 's/enable-welcome-root=\"true\"/enable-welcome-root=\"false\"/' $CONFIG_FILE",

                // Disable Management security (!) by deleting the security-realm attribute
                "sed -i.bk 's/http-interface security-realm=\"ManagementRealm\"/http-interface/' $CONFIG_FILE",

                // Increase deployment timeout to ten minutes
                "sed -i.bk 's/\\(path=\"deployments\"\\)/\\1 deployment-timeout=\"600\"/' $CONFIG_FILE",

                // TODO: This sed is no longer applicable in 7.1.1.
				"sed -i.bk 's/inet-address value=\"127.0.0.1\"/any-address/' $CONFIG_FILE"
			).execute();
		
		entity.deployInitialWars()
	}
	
	@Override
	public void launch() {
		newScript(LAUNCHING, usePidFile:true).
			body.append(
				"$installDir/jboss-as-${version}/bin/${SERVER_TYPE}.sh "+
					"--server-config $CONFIG_FILE "+
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
