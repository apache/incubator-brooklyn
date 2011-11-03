package brooklyn.entity.basic.lifecycle;

import java.io.File;
import java.util.List
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.location.basic.SshMachineLocation

public abstract class StartStopSshDriver extends AbstractStartStopDriver implements ScriptRunner {

	public static final Logger log = LoggerFactory.getLogger(StartStopSshDriver.class);
	
    public static final String BROOKLYN_HOME_DIR = "/tmp/brooklyn";
    public static final String DEFAULT_INSTALL_BASEDIR = BROOKLYN_HOME_DIR+"/"+"installs";
	public static final String NO_VERSION_INFO = "no-version-info";

	public StartStopSshDriver(EntityLocal entity, SshMachineLocation machine) {
		super(entity, machine);
	}

	protected String getDefaultVersion() { NO_VERSION_INFO }
	
    protected String getVersion() {
		entity.getConfig(SoftwareProcessEntity.SUGGESTED_VERSION, getDefaultVersion())
	}
	
	protected String getEntityVersionLabel(String separator="_") {
		(entity.getClass().getSimpleName() ?: entity.getClass().getName())+
		(getVersion()!=NO_VERSION_INFO ? separator+getVersion() : "");
	}
    protected String getInstallDir() {
		entity.getConfig(SoftwareProcessEntity.SUGGESTED_INSTALL_DIR) ?:
		DEFAULT_INSTALL_BASEDIR+"/"+getEntityVersionLabel("/")
	}
    protected String getRunDir() {
		entity.getConfig(SoftwareProcessEntity.SUGGESTED_RUN_DIR) ?:
		BROOKLYN_HOME_DIR+"/"+"apps"+"/"+entity.application.id+"/"+"entities"+"/"+
			entityVersionLabel+"_"+entity.id
	}

	public SshMachineLocation getMachine() { location }
	public String getHostname() { entity.getAttribute(Attributes.HOSTNAME) }

	public int execute(List<String> script, String summaryForLogging) {
		log.info("{} on machine {}: {}", summaryForLogging, machine, script)
		// TODO log in a better place/way
		machine.run(out:System.out, err:System.err, script, getShellEnvironment());
	}
	
	/**
	* The environment variables to be set when executing the commands (for install, run, check running, etc).
	*/
   public Map<String, String> getShellEnvironment() {
	   [:] << entity.getConfig(SoftwareProcessEntity.SHELL_ENVIRONMENT, [:])
   }
	
	protected final static String INSTALLING = "installing";
	protected final static String CUSTOMIZING = "customizing";
	protected final static String LAUNCHING = "launching";
	protected final static String CHECK_RUNNING = "check-running";
	protected final static String STOPPING = "stopping";

	/** sets up a script for the given phase
	 * (e.g. INSTALLING, LAUNCHING, etc)
	 * <p>
	 * flags supported include:
	 * - usePidFile: true, or a filename, meaning to create (for launching) that pid
	 * @param phase
	 * @return
	 */
	protected ScriptHelper newScript(Map flags=[:], String phase) {
		def s = new ScriptHelper(this, phase+" "+this);
		if (phase==INSTALLING) {
			s.header.append(
				'export INSTALL_DIR="'+installDir+'"',
				'mkdir -p $INSTALL_DIR',
				'cd $INSTALL_DIR',
				'test -f BROOKLYN && exit 0',
			).footer.append(
				'date > $INSTALL_DIR/BROOKLYN'
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
		if (phase in [INSTALLING, LAUNCHING])
			s.failOnNonZeroResultCode()

		if (flags.usePidFile) {
			String pidFile = (flags.usePidFile in String ? flags.usePidFile : "${runDir}/pid.txt")
			if (phase in [LAUNCHING])
				s.footer.prepend("echo \$! > ${pidFile}")
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

	public Set<Integer> getPortsUsed() { super.getPortsUsed() + [22] }
	
}
