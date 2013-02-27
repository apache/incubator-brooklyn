package brooklyn.entity.database.mysql

import static brooklyn.entity.basic.lifecycle.CommonCommands.downloadUrlAs
import static brooklyn.entity.basic.lifecycle.CommonCommands.installPackage
import static brooklyn.entity.basic.lifecycle.CommonCommands.ok
import static java.lang.String.format

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.drivers.downloads.DownloadResolver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicOsDetails.OsVersions
import brooklyn.util.ComparableVersion
import brooklyn.util.ResourceUtils
import brooklyn.util.text.Identifiers
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap

/**
 * The SSH implementation of the {@link MySqlDriver}.
 */
public class MySqlSshDriver extends AbstractSoftwareProcessSshDriver implements MySqlDriver{

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);

    private String _expandedInstallDir;
        
    public MySqlSshDriver(MySqlNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    protected String getVersion() {
        entity.getConfig(MySqlNode.SUGGESTED_VERSION) ?: getDefaultVersion();
    }

    public String getOsTag() {
//      e.g. "osx10.6-x86_64"; see http://www.mysql.com/downloads/mysql/#downloads
        def os = getLocation().getOsDetails();
        if (!os) return "linux2.6-i686";
        if (os.isMac()) {
            String osp1 = os.version==null ? "osx10.5" //lowest common denominator
                : new ComparableVersion(os.version).isGreaterThanOrEqualTo(OsVersions.MAC_10_6) ? "osx10.6"
                : new ComparableVersion(os.version).isGreaterThanOrEqualTo(OsVersions.MAC_10_5) ? "osx10.5"
                : "osx10.5";  //lowest common denominator
            String osp2 = os.is64bit() ? "x86_64" : "x86"
            return osp1+"-"+osp2;
        }
        //assume generic linux
        String osp1 = "linux2.6";
        String osp2 = os.is64bit() ? "x86_64" : "i686"
        return osp1+"-"+osp2;
    }
	
	public String getMirrorUrl() {
        entity.getConfig(MySqlNode.MIRROR_URL);
	}
    
    public String getBasedir() { return getExpandedInstallDir(); }
	
    public String getDatadir() {
        String result = entity.getConfig(MySqlNode.DATA_DIR);
        return (result == null) ? "." : result;
    }

    public String getInstallFilename() {
        return String.format("mysql-%s-%s.tar.gz", getVersion(), getOsTag());
    }
    
    private String getExpandedInstallDir() {
        if (_expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return _expandedInstallDir;
    }
    
    @Override
    public void install() {
        //mysql-${version}-${driver.osTag}.tar.gz
        
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsRegistry().resolve(
                this, ImmutableMap.of("filename", getInstallFilename()));
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        _expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectorName(format("mysql-%s-%s", getVersion(), getOsTag()));
        
        List<String> commands = new LinkedList<String>();
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("echo installing extra packages")
        commands.add(installPackage(yum: "libgcc_s.so.1 libaio.so.1 libncurses.so.5", apt: "libaio1 libaio-dev", null));

        // these deps are needed on some OS versions but others don't need them so ignore failures (ok(...))
        commands.add(ok(installPackage(yum: "libaio", apt: "ia32-libs", null)));
        commands.add("echo finished installing extra packages")
        commands.addAll(downloadUrlAs(urls, saveAs));
        commands.add("tar xfvz ${saveAs}");

        newScript(INSTALLING).
            body.append(commands).execute();
    }

    final String socketUid = Identifiers.makeRandomId(6);
    String secretPassword = Identifiers.makeRandomId(6);
    public String getPassword() { secretPassword }
    public MySqlNodeImpl getEntity() { return super.getEntity() }
    public int getPort() { return entity.port }
    
    @Override
    public void customize() {
        newScript(CUSTOMIZING).
			body.append("echo copying creation script").
			execute();  //create the directory
        Reader creationScript;
        String url = entity.getConfig(MySqlNode.CREATION_SCRIPT_URL)
        if (!Strings.isBlank(url)) creationScript = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(url));
        else creationScript = new StringReader(entity.getConfig(MySqlNode.CREATION_SCRIPT_CONTENTS)?:"")
		machine.copyTo(creationScript, runDir+"/"+"creation-script.cnf");
        newScript(CUSTOMIZING).
            updateTaskAndFailOnNonZeroResultCode().
            body.append(
                "cat > mymysql.cnf << END_MYSQL_CONF_${entity.id}\n"+"""
[client]
port            = ${port}
socket          = /tmp/mysql.sock.${socketUid}.${port}
user            = root
password        = ${password}
                
# Here follows entries for some specific programs
                
# The MySQL server
[mysqld]
port            = ${port}
socket          = /tmp/mysql.sock.${socketUid}.${port}
basedir         = ${basedir}
datadir         = ${datadir}
"""+getMySqlServerOptionsString()+"""

"""+"END_MYSQL_CONF_${entity.id}\n",
                "${basedir}/scripts/mysql_install_db "+
                    "--basedir=${basedir} --datadir=${datadir} "+
                    "--defaults-file=mymysql.cnf",
                "${basedir}/bin/mysqld --defaults-file=mymysql.cnf --user=`whoami` &", //--user=root needed if we are root
                "export MYSQL_PID=\$!",
                "sleep 20",
                "echo launching mysqladmin",
                "${basedir}/bin/mysqladmin --defaults-file=mymysql.cnf --password= password ${password}",
                "sleep 20",
                "echo launching mysql creation script",
                "${basedir}/bin/mysql --defaults-file=mymysql.cnf < creation-script.cnf",
                "echo terminating mysql on customization complete",
                "kill \$MYSQL_PID"
            ).execute();
    }
    
    protected String getMySqlServerOptionsString() {
        Map options = entity.getConfig(MySqlNode.MYSQL_SERVER_CONF);
        if (!options) return "";
        String result = "";
        options.each { k,v -> result += ""+k+" = "+v+"\n" }
        return result;
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile: true).
            updateTaskAndFailOnNonZeroResultCode().
            body.append(
                "nohup ${basedir}/bin/mysqld --defaults-file=mymysql.cnf --user=`whoami` > out.log 2> err.log < /dev/null &", 
            ).execute();
    }

    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile: true).execute() == 0
    }

    @Override
    public void stop() {
        newScript(STOPPING, usePidFile: true).execute();
    }

    @Override
    public void kill() {
        newScript(KILLING, usePidFile: true).execute();
    }
}
