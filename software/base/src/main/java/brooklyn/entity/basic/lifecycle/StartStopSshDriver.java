package brooklyn.entity.basic.lifecycle;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class StartStopSshDriver extends AbstractStartStopDriver implements ScriptRunner {

    public static final Logger log = LoggerFactory.getLogger(StartStopSshDriver.class);
    public static final Logger logSsh = LoggerFactory.getLogger(BrooklynLogging.SSH_IO);

    public static final String BROOKLYN_HOME_DIR = "/tmp/brooklyn-"+System.getProperty("user.name");
    public static final String DEFAULT_INSTALL_BASEDIR = BROOKLYN_HOME_DIR+"/"+"installs";
    public static final String NO_VERSION_INFO = "no-version-info";

    public StartStopSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    /**
     * @deprecated will be deleted in 0.5. Set default on ConfigKey in entity? Rather than overriding it here and not
     * telling the entity what value was chosen!
     */
    @Deprecated
    protected String getDefaultVersion() { return NO_VERSION_INFO; }

    /** returns location (tighten type, since we know it is an ssh machine location here) */	
    public SshMachineLocation getLocation() {
        return (SshMachineLocation) super.getLocation();
    }

    protected String getVersion() {
        return elvis(getEntity().getConfig(SoftwareProcessEntity.SUGGESTED_VERSION), getDefaultVersion());
    }

    protected String getEntityVersionLabel() {
        return getEntityVersionLabel("_");
    }
    protected String getEntityVersionLabel(String separator) {
        return elvis(entity.getClass().getSimpleName(),  
                entity.getClass().getName())+(getVersion()!=NO_VERSION_INFO ? separator+getVersion() : "");
    }
    public String getInstallDir() {
        return elvis(entity.getConfig(SoftwareProcessEntity.SUGGESTED_INSTALL_DIR),
                DEFAULT_INSTALL_BASEDIR+"/"+getEntityVersionLabel("/"));
    }
    public String getRunDir() {
        return elvis(entity.getConfig(SoftwareProcessEntity.SUGGESTED_RUN_DIR), 
                BROOKLYN_HOME_DIR+"/"+"apps"+"/"+entity.getApplication().getId()+"/"+"entities"+"/"+
                getEntityVersionLabel()+"_"+entity.getId());
    }

    public SshMachineLocation getMachine() { return getLocation(); }
    public String getHostname() { return entity.getAttribute(Attributes.HOSTNAME); }

    public int execute(List<String> script, String summaryForLogging) {
        return execute(Maps.newLinkedHashMap(), script, summaryForLogging);
    }
    
    public int execute(Map flags2, List<String> script, String summaryForLogging) {
        Map flags = Maps.newLinkedHashMap(flags2);
        Map<String, String> environment = (Map<String, String>) ((flags.get("env") != null) ? flags.get("env") : getShellEnvironment());
        if (!flags.containsKey("logPrefix")) flags.put("logPrefix", ""+entity.getId()+"@"+getLocation().getName());
        return getMachine().execScript(flags, summaryForLogging, script, environment);
    }

    /**
     * The environment variables to be set when executing the commands (for install, run, check running, etc).
     */
    public Map<String, String> getShellEnvironment() {
        return Maps.newLinkedHashMap(entity.getConfig(SoftwareProcessEntity.SHELL_ENVIRONMENT, Collections.emptyMap()));
    }

    public void copyFile(File src, String destination) {
        getMachine().copyTo(src, destination);
    }

    public void copyFile(File src, File destination) {
        getMachine().copyTo(src, destination);
    }

    protected final static String INSTALLING = "installing";
    protected final static String CUSTOMIZING = "customizing";
    protected final static String LAUNCHING = "launching";
    protected final static String CHECK_RUNNING = "check-running";
    protected final static String STOPPING = "stopping";
    protected final static String RESTARTING = "restarting";
    
    public final static String PID_FILENAME = "pid.txt";

    /** sets up a script for the given phase, including default wrapper commands
     * (e.g. INSTALLING, LAUNCHING, etc)
     * <p>
     * flags supported include:
     * - usePidFile: true, or a filename, meaning to create (for launching) that pid
     * @param phase
     * @return
     */
    protected ScriptHelper newScript(String phase) {
        return newScript(Maps.newLinkedHashMap(), phase);
    }
    protected ScriptHelper newScript(Map flags, String phase) {
        ScriptHelper s = new ScriptHelper(this, phase+" "+elvis(entity,this));
        if (!truth(flags.get("nonStandardLayout"))) {
            if (phase==INSTALLING) {
                s.useMutex(getLocation(), getInstallDir(), "installing "+elvis(entity,this));
                s.header.append(
                        "export INSTALL_DIR=\""+getInstallDir()+"\"",
                        "mkdir -p $INSTALL_DIR",
                        "cd $INSTALL_DIR",
                        "test -f BROOKLYN && exit 0"
                        ).footer.append(
                        "date > $INSTALL_DIR/BROOKLYN"
                        );
            }
            if (ImmutableSet.of(CUSTOMIZING, LAUNCHING, CHECK_RUNNING, STOPPING, RESTARTING).contains(phase)) {
                s.header.append(
                        "export RUN_DIR=\""+getRunDir()+"\"",
                        "mkdir -p $RUN_DIR",
                        "cd $RUN_DIR"
                        );
            }
        }

        if (ImmutableSet.of(CUSTOMIZING).contains(phase))
            s.skipIfBodyEmpty();
        if (ImmutableSet.of(CHECK_RUNNING, LAUNCHING, STOPPING, RESTARTING).contains(phase))
            s.failIfBodyEmpty();
        if (ImmutableSet.of(INSTALLING, LAUNCHING).contains(phase))
            s.failOnNonZeroResultCode();

        if (truth(flags.get("usePidFile"))) {
            String pidFile = (flags.get("usePidFile") instanceof CharSequence ? flags.get("usePidFile") : getRunDir()+"/"+PID_FILENAME).toString();
            if (LAUNCHING.equals(phase))
                s.footer.prepend("echo $! > "+pidFile);
            else if (CHECK_RUNNING.equals(phase))
                s.body.append(
                        "test -f "+pidFile+" || exit 1", //no pid, not running

                        //old method, for supplied service, or entity.id
                        //					"ps aux | grep ${service} | grep \$(cat ${pidFile}) > /dev/null"
                        //new way, preferred?
                        "ps -p `cat "+pidFile+"`"

                        ).requireResultCode(Predicates.or(Predicates.equalTo(0), Predicates.equalTo(1)));
            // 1 is not running

            else if (STOPPING.equals(phase))
                s.body.append(
                        "export PID=`cat "+pidFile+"`",
                        "[[ -n \"$PID\" ]] || exit 0",
                        "kill $PID",
                        "kill -9 $PID",
                        "rm "+pidFile
                        );
                
            else if (RESTARTING.equals(phase))
                s.footer.prepend(
                        "test -f "+pidFile+" || exit 1", //no pid, not running
                        "ps -p `cat "+pidFile+"` || exit 1" //no process; can't restart,
                        );
            // 1 is not running

            else
                log.warn("usePidFile script option not valid for "+s.summary);
        }

        return s;
    }

    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet();
        result.add(22);
        return result;
    }

}
