package brooklyn.entity.database.mysql;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicOsDetails.OsArchs
import brooklyn.location.basic.BasicOsDetails.OsVersions
import brooklyn.util.ComparableVersion

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
        String file = '$'+"HOME/.brooklyn/repository/${entityVersionLabel}/${saveAs}";
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(
                "URL=$url",
                "FILE=$file",
                "if [ -f \$FILE ]; then cp \$FILE ./$saveAs; else curl -L \"${url}\" -o ${saveAs}; fi || exit 9",
                "curl -L \"${url}\" -o ${saveAs} || exit 9",
                "tar xfv"+(saveAs.endsWith("z") ? "z" : "")+" ${saveAs}",
            ).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
            failOnNonZeroResultCode().
            body.append(
                "'${basedir}/mysql_install_db' '--basedir=${basedir}' --datadir=.",
                
//                --socket=/tmp/mysql.sock.${port} --port=${port} -u root
            ).execute();

        //TODO put this in a file        
//        [client]
//        password       = secret_password
//        port            = 3307
//        socket          = /tmp/mysql.sock.3307
//        
//        # Here follows entries for some specific programs
//        
//        # The MySQL server
//        [mysqld]
//        port            = 3307
//        socket          = /tmp/mysql.sock.3307
        
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile: true).
            failOnNonZeroResultCode().
            body.append(
                "nohup '${basedir}/bin/mysqld' --basedir='${basedir}' --datadir=. &"
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
