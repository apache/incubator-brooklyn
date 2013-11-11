package brooklyn.entity.database.mariadb;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.ok;
import static java.lang.String.format;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

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
        copyDatabaseCreationScript();
        copyDatabaseConfigScript();

        newScript(CUSTOMIZING).
            updateTaskAndFailOnNonZeroResultCode().
            body.append(
                "chmod 600 my.cnf",
                getBasedir()+"/scripts/mysql_install_db "+
                    "--basedir="+getBasedir()+" --datadir="+getDatadir()+" "+
                    "--defaults-file=my.cnf",
                getBasedir()+"/bin/mysqld --defaults-file=my.cnf --user=`whoami` &", //--user=root needed if we are root
                "export MYSQL_PID=$!",
                "sleep 20",
                "echo launching mysqladmin",
                getBasedir()+"/bin/mysqladmin --defaults-file=my.cnf --password= password "+getPassword(),
                "sleep 20",
                "echo launching mysql creation script",
                getBasedir()+"/bin/mysql --defaults-file=my.cnf < creation-script.cnf",
                "echo terminating mysql on customization complete",
                "kill $MYSQL_PID"
            ).execute();
    }

    private void copyDatabaseCreationScript() {
        newScript(CUSTOMIZING).
                body.append("echo copying creation script").
                execute();  //create the directory

        Reader creationScript;
        String url = entity.getConfig(MariaDbNode.CREATION_SCRIPT_URL);
        if (!Strings.isBlank(url))
            creationScript = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(url));
        else creationScript =
                new StringReader((String) elvis(entity.getConfig(MariaDbNode.CREATION_SCRIPT_CONTENTS), ""));
        getMachine().copyTo(creationScript, getRunDir() + "/creation-script.cnf");
    }

    private void copyDatabaseConfigScript() {
        newScript(CUSTOMIZING).
                body.append("echo copying server config script").
                execute();  //create the directory

        String configScriptContents = processTemplate(entity.getAttribute(MariaDbNode.TEMPLATE_CONFIGURATION_URL));
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, getRunDir() + "/my.cnf");
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

}
