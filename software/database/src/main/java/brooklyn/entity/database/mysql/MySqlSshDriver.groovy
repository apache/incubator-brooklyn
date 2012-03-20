package brooklyn.entity.database.mysql;

import org.slf4j.Logger

import org.slf4j.LoggerFactory

import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicOsDetails.OsArchs
import brooklyn.location.basic.BasicOsDetails.OsVersions
import brooklyn.util.ComparableVersion
import brooklyn.entity.basic.lifecycle.CommonCommands;

public class MySqlSshDriver extends StartStopSshDriver {

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);
    
    public MySqlSshDriver(MySqlNode entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    protected String getVersion() {
        String v = entity.getConfig(SoftwareProcessEntity.SUGGESTED_VERSION) ?: getDefaultVersion();
        if (osTag.contains("linux") && v.equals("5.5.21"))
            v = "5.5.21-1";  //special mangling required for linux
        return v;
    }

    public String getOsTag() {
//      e.g. "osx10.6-x86_64"; see http://www.mysql.com/downloads/mysql/#downloads
        def os = getLocation().getOsDetails();
        if (!os) return "linux2.6.i386";
        if (os.isMac()) {
            String osp1 = os.version==null ? "osx10.5" //lowest common denominator
                : new ComparableVersion(os.version).isGreaterThanOrEqualTo(OsVersions.MAC_10_6) ? "osx10.6"
                : new ComparableVersion(os.version).isGreaterThanOrEqualTo(OsVersions.MAC_10_5) ? "osx10.5"
                : "osx10.5";  //lowest common denominator
            String osp2 = os.arch.equals(OsArchs.X_86_64) ? "x86_64" : "x86"
            return osp1+"-"+osp2;
        }
        //assume generic linux
        String osp1 = "linux";
        String osp2 = os.arch.equals(OsArchs.X_86_64) ? "x86_64" : "i386"
        return osp1+"."+osp2;   //note . here
    }
    public String getSuffix() {
        def os = getLocation().getOsDetails();
        //they don't offer a consistent set of downloads...
        if (os.isMac()) return "tar.gz";
        if (os.isLinux()) return "tar";
        //guess
        return "tar";
    }
    public String getUrl() { "http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-${version}-${osTag}.${suffix}/from/http://gd.tuwien.ac.at/db/mysql/" }
    
    public String getBasedir() { installDir+"/mysql-${version}-${osTag}" }
    @Override
    public void install() {
        String saveAs  = "mysql-${version}-${osTag}.${suffix}"
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(
                CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs),
                CommonCommands.INSTALL_TAR, 
                "tar xfv"+(saveAs.endsWith("z") ? "z" : "")+" ${saveAs}",  //because they don't offer a consistent set of downloads
            ).execute();
    }

    String secretPassword = "random"+(int)(Math.random()*100000)
    public String getPassword() { secretPassword }
    public MySqlNode getEntity() { return super.getEntity() }
    public int getPort() { return entity.port }
    
    @Override
    public void customize() {
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
                "cat > creation-script.cnf << END_MYSQL_CONF_${entity.id}\n"+
                    entity.getConfig(MySqlNode.CREATION_SCRIPT)+"\n"+"END_MYSQL_CONF_${entity.id}\n",
                "${basedir}/bin/mysqld --defaults-file=mymysql.cnf &",
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
                "nohup ${basedir}/bin/mysqld --defaults-file=mymysql.cnf &",
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
