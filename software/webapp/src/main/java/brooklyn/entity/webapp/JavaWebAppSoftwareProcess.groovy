package brooklyn.entity.webapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver
import brooklyn.location.basic.SshMachineLocation


public abstract class JavaWebAppSoftwareProcess extends SoftwareProcessEntity implements JavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppSoftwareProcess.class)
    
	
	public JavaWebAppSoftwareProcess(Map flags=[:], Entity owner=null) {
		super(flags, owner)
	}

	@Override
	protected void connectSensors() {
		super.connectSensors();
		
		WebAppServiceMethods.connectWebAppServerPolicies(this);

	}
	
	//just provide better typing
	public JavaWebAppSshDriver getDriver() { super.getDriver() }
	
	public void deployInitialWars() {
		def rootWar = getConfig(ROOT_WAR);
		if (rootWar) driver.deploy(rootWar, "ROOT.war")
		
		def namedWars = getConfig(NAMED_WARS, []);
		namedWars.each { String it ->
			String name = it.substring(it.lastIndexOf('/')+1);
			driver.deploy(it, name)
		}
	}

	//TODO deploy effector
	
	@Override
	public void stop() {
		super.stop()
		// zero our workrate derived workrates.
		// TODO might not be enough, as policy may still be executing and have a record of historic vals; should remove policies
		// (also not sure we want this; implies more generally a responsibility for sensors to announce things when disconnected,
		// vs them just showing the last known value...)
		setAttribute(REQUESTS_PER_SECOND, 0)
		setAttribute(AVG_REQUESTS_PER_SECOND, 0)
	}

}

public abstract class JavaWebAppSshDriver extends JavaStartStopSshDriver {

	public JavaWebAppSshDriver(JavaWebAppSoftwareProcess entity, SshMachineLocation machine) {
		super(entity, machine)
	}

	public JavaWebAppSoftwareProcess getEntity() { super.getEntity() }
    public Integer getHttpPort() { return entity.getAttribute(Attributes.HTTP_PORT) }	

    @Override
    public void postLaunch() {
        assert httpPort!=null : "HTTP_PORT sensor not set; is an acceptable port available?"
        entity.setAttribute(WebAppService.ROOT_URL, "http://${hostname}:${httpPort}/")
    }

	protected abstract String getDeploySubdir();
	
	public void deploy(File f, String targetName=null) {
		if (!targetName) targetName = f.getName();
        def dest = runDir+"/"+deploySubdir+"/"+targetName
		log.info "{} deploying {} to {}:{}", entity, f, hostname, dest
		machine.run "mv -f ${dest} ${dest}.bak > /dev/null 2>&1" //back up old file/directory
        int result = machine.copyTo(f, dest);
		log.debug "{} deployed {} to {}:{}: result {}", entity, f, hostname, dest, result
	}
	public void deploy(String url, String targetName) {
        def dest = runDir+"/"+deploySubdir+"/"+targetName
        log.info "{} deploying {} to {}:{}", entity, url, hostname, dest
        machine.run "mv -f ${dest} ${dest}.bak > /dev/null 2>&1" //back up old file/directory
        // TODO backup not supported, is it?
        int result = machine.copyTo(backup:true, getResource(url), runDir+"/"+deploySubdir+"/"+targetName)
		log.debug "{} deployed {} to {}:{}: result {}", entity, url, hostname, dest, result
	}

}
