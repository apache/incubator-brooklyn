package brooklyn.entity.database.mysql;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicOsDetails.OsArchs
import brooklyn.location.basic.BasicOsDetails.OsVersions
import brooklyn.util.ComparableVersion
import brooklyn.util.ResourceUtils

public class MySqlSshDriver extends StartStopSshDriver {

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
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(
                CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs),
                CommonCommands.INSTALL_TAR, 
                "tar xfvz ${saveAs}",
                "(which apt-get && apt-get install libaio1) || echo skipping libaio installation"
            ).execute();
    }

    String secretPassword = "random"+(int)(Math.random()*100000)
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
socket          = /tmp/mysql.sock.${port}
user            = root
password        = ${password}
                
# Here follows entries for some specific programs
                
# The MySQL server
[mysqld]
port            = ${port}
socket          = /tmp/mysql.sock.${port}
basedir         = ${basedir}
datadir         = .

"""+"END_MYSQL_CONF_${entity.id}\n",
                "${basedir}/scripts/mysql_install_db "+
                    "--basedir=${basedir} --datadir=. "+
                    "--defaults-file=mymysql.cnf",
                "${basedir}/bin/mysqld --defaults-file=mymysql.cnf --user=`whoami` &", //--user=root needed if we are root
                "export MYSQL_PID=\$!",
                "sleep 5 && ${basedir}/bin/mysqladmin --defaults-file=mymysql.cnf --password= password ${password}",
                "${basedir}/bin/mysql --defaults-file=mymysql.cnf < creation-script.cnf",
                "kill \$MYSQL_PID"
            ).execute();
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile: true).
            failOnNonZeroResultCode().
            body.append(
                "nohup ${basedir}/bin/mysqld --defaults-file=mymysql.cnf --user=`whoami` &",  //FIXME
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
