package brooklyn.entity.database.mariadb;

import static brooklyn.util.GroovyJavaMethods.truth;
import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.ok;
import static java.lang.String.format;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableMap;

/**
 * The SSH implementation of the {@link MariaDbDriver}.
 */
public class MariaDbSshDriver extends AbstractSoftwareProcessSshDriver implements MariaDbDriver {

    public static final Logger log = LoggerFactory.getLogger(MariaDbSshDriver.class);

    private String expandedInstallDir;

    public MariaDbSshDriver(MariaDbNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        // NOTE: cannot rely on OsDetails.isLinux() to return true for all linux flavours, so
        // explicitly test for unsupported OSes, otherwise assume generic linux.
        if (os == null) return "linux-i686";
        if (os.isWindows() || os.isMac())
            throw new UnsupportedOperationException("only support linux versions just now; OS details: " + os);
        return "linux-" + (os.is64bit() ? "x86_64" : "i686");
    }

    public String getDownloadParentDir() {
        // NOTE: cannot rely on OsDetails.isLinux() to return true for all linux flavours, so
        // explicitly test for unsupported OSes, otherwise assume generic linux.
        OsDetails os = getLocation().getOsDetails();
        if (os == null) return "kvm-bintar-hardy-x86";
        if (os.isWindows() || os.isMac())
            throw new UnsupportedOperationException("only support linux versions just now; OS details: " + os);
        return "kvm-bintar-hardy-" + (os.is64bit() ? "amd64" : "x86");
    }

    public String getMirrorUrl() {
        return entity.getConfig(MariaDbNode.MIRROR_URL);
    }

    public String getBasedir() {
        return getExpandedInstallDir();
    }

    public String getDatadir() {
        String result = entity.getConfig(MariaDbNode.DATA_DIR);
        return (result == null) ? "." : result;
    }

    public String getInstallFilename() {
        return String.format("mariadb-%s-%s.tar.gz", getVersion(), getOsTag());
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null)
            throw new IllegalStateException("'expandedInstallDir' is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    public void install() {
        // mariadb-${version}-${driver.osTag}.tar.gz

        ManagementContext managementContext = ((EntityInternal) entity).getManagementContext();
        DownloadResolverManager downloadManager = managementContext.getEntityDownloadsManager();
        DownloadResolver resolver = downloadManager.newDownloader(
                this, ImmutableMap.of("filename", getInstallFilename()));
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("mariadb-%s-%s", getVersion(), getOsTag()));

        List<String> commands = new LinkedList<String>();
        commands.add(BashCommands.INSTALL_TAR);
        commands.add(BashCommands.INSTALL_CURL);

        commands.add("echo installing extra packages");
        commands.add(installPackage(ImmutableMap.of("yum", "libgcc_s.so.1"), null));
        commands.add(installPackage(ImmutableMap.of("yum", "libaio.so.1 libncurses.so.5", "apt", "libaio1 libaio-dev"), null));
        // these deps are needed on some OS versions but others don't need them so ignore failures (ok(...))
        commands.add(ok(installPackage(ImmutableMap.of("yum", "libaio", "apt", "ia32-libs"), null)));
        commands.add("echo finished installing extra packages");

        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(format("tar xfvz %s", saveAs));

        newScript(INSTALLING).
            body.append(commands).execute();
    }

    public MariaDbNodeImpl getEntity() { return (MariaDbNodeImpl) super.getEntity(); }
    public int getPort() { return getEntity().getPort(); }
    public String getSocketUid() { return getEntity().getSocketUid(); }
    public String getPassword() { return getEntity().getPassword(); }

    @Override
    public void customize() {
        copyDatabaseConfigScript();

        newScript(CUSTOMIZING).
            updateTaskAndFailOnNonZeroResultCode().
            body.append(
                "chmod 600 mymysql.cnf",
                getBasedir()+"/scripts/mysql_install_db "+
                    "--basedir="+getBasedir()+" --datadir="+getDatadir()+" "+
                    "--defaults-file=my.cnf"
            ).execute();

        // launch, then we will configure it
        launch();
        
        CountdownTimer timer = Duration.seconds(20).countdownTimer();
        boolean hasCreationScript = copyDatabaseCreationScript();
        timer.waitForExpiryUnchecked();

        DynamicTasks.queue(
            SshEffectorTasks.ssh(
                "cd "+getRunDir(),
                getBasedir()+"/bin/mysqladmin --defaults-file=my.cnf --password= password "+getPassword()
            ).summary("setting password"));

        if (hasCreationScript)
            executeScriptFromInstalledFileAsync("creation-script.sql");

        // not sure necessary to stop then subsequently launch, but seems safest
        // (if skipping, use a flag in launch to indicate we've just launched it)
        stop();
    }
    
    private void copyDatabaseConfigScript() {
        newScript(CUSTOMIZING).
                body.append("echo copying config script").
                execute();  //create the directory

        String configScriptContents = processTemplate(entity.getAttribute(MariaDbNode.TEMPLATE_CONFIGURATION_URL));
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, getRunDir() + "/my.cnf");
    }

    private boolean copyDatabaseCreationScript() {
        InputStream creationScript = DatastoreMixins.getDatabaseCreationScript(entity);
        if (creationScript==null) 
            return false;
        getMachine().copyTo(creationScript, getRunDir() + "/creation-script.sql");
        return true;
    }

    public String getMariaDbServerOptionsString() {
        Map<String, Object> options = entity.getConfig(MariaDbNode.MARIADB_SERVER_CONF);
        if (!truth(options)) return "";
        String result = "";
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            if("".equals(entry.getValue())){
                result += ""+entry.getKey()+"\n";
            }else{
                result += ""+entry.getKey()+" = "+entry.getValue()+"\n";
            }
        }
        return result;
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", true), LAUNCHING).
            updateTaskAndFailOnNonZeroResultCode().
            body.append(
                format("nohup %s/bin/mysqld --defaults-file=my.cnf --user=`whoami` > out.log 2> err.log < /dev/null &", getBasedir()) 
            ).execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
            .body.append(getStatusCmd())
            .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

    @Override
    public String getStatusCmd() {
        // TODO Is this very bad, to include the password in the command being executed 
        // (so is in `ps` listing temporarily, and in .bash_history)
        return format("%s/bin/mysqladmin --user=%s --password=%s --socket=/tmp/mysql.sock.%s.%s status", 
                getExpandedInstallDir(), "root", getPassword(), getSocketUid(), getPort());
    }
    
    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands) {
        String filename = "mariadb-commands-"+Identifiers.makeRandomId(8);
        DynamicTasks.queue(SshEffectorTasks.put(Urls.mergePaths(getRunDir(), filename)).contents(commands).summary("copying datastore script to execute "+filename));
        return executeScriptFromInstalledFileAsync(filename);
    }

    public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String filenameAlreadyInstalledAtServer) {
        return DynamicTasks.queue(SshEffectorTasks.ssh(
            "cd "+getRunDir(), 
            getBasedir()+"/bin/mysql --defaults-file=my.cnf < "+filenameAlreadyInstalledAtServer)
            .summary("executing datastore script "+filenameAlreadyInstalledAtServer));
    }

}
