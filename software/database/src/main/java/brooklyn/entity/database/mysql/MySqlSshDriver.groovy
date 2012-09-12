package brooklyn.entity.database.mysql

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicOsDetails.OsArchs
import brooklyn.location.basic.BasicOsDetails.OsVersions
import brooklyn.util.ComparableVersion
import brooklyn.util.IdGenerator;
import brooklyn.util.ResourceUtils

/**
 * The SSH implementation of the {@link MySlDriver}.
 */
public class MySqlSshDriver extends AbstractSoftwareProcessSshDriver implements MySqlDriver{

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);
    
    public MySqlSshDriver(MySqlNode entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    protected String getVersion() {
        entity.getConfig(SoftwareProcessEntity.SUGGESTED_VERSION) ?: getDefaultVersion();
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
            String osp2 = os.arch.equals(OsArchs.X_86_64) ? "x86_64" : "x86"
            return osp1+"-"+osp2;
        }
        //assume generic linux
        String osp1 = "linux2.6";
        String osp2 = os.arch.equals(OsArchs.X_86_64) ? "x86_64" : "i686"
        return osp1+"-"+osp2;
    }
	
	public String getMirrorUrl() {
        entity.getConfig(MySqlNode.MIRROR_URL);
//		"http://mysql.mirrors.pair.com/"
//		"http://gd.tuwien.ac.at/db/mysql/"
	}
    
	public String getBasename() { "mysql-${version}-${osTag}" }
    
	//http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-5.5.21-osx10.6-x86_64.tar.gz/from/http://gd.tuwien.ac.at/db/mysql/
	//http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-5.5.21-linux2.6-i686.tar.gz/from/http://gd.tuwien.ac.at/db/mysql/
	public String getUrl() { "http://dev.mysql.com/get/Downloads/MySQL-5.5/${basename}.tar.gz/from/${mirrorUrl}" }
    
    public String getBasedir() { installDir+"/"+basename }
	
    @Override
    public void install() {
        String saveAs  = "${basename}.tar.gz"
        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xfvz ${saveAs}");
        commands.add("(which apt-get && apt-get install libaio1) || echo skipping libaio installation");

        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(commands).execute();
    }

    final String socketUid = IdGenerator.makeRandomId(6);
    String secretPassword = IdGenerator.makeRandomId(6);
    public String getPassword() { secretPassword }
    public MySqlNode getEntity() { return super.getEntity() }
    public int getPort() { return entity.port }
    
    @Override
    public void customize() {
        newScript(CUSTOMIZING).
			body.append("echo copying creation script").
			execute();  //create the directory
        Reader creationScript;
        def url = entity.getConfig(MySqlNode.CREATION_SCRIPT_URL)
        if (url) creationScript = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(url));
        else creationScript = new StringReader(entity.getConfig(MySqlNode.CREATION_SCRIPT_CONTENTS)?:"")
		machine.copyTo(creationScript, runDir+"/"+"creation-script.cnf");
        newScript(CUSTOMIZING).
            failOnNonZeroResultCode().
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
datadir         = .

"""+"END_MYSQL_CONF_${entity.id}\n",
                "${basedir}/scripts/mysql_install_db "+
                    "--basedir=${basedir} --datadir=. "+
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

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile: true).
            failOnNonZeroResultCode().
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

}
