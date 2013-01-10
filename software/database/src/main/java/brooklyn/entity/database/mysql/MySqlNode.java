package brooklyn.entity.database.mysql;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ISoftwareProcessEntity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MySqlNodeImpl.class)
public interface MySqlNode extends ISoftwareProcessEntity {

    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "5.5.29");

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MYSQL_PORT = new PortAttributeSensorAndConfigKey("mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+"));

    @SetFromFlag("creationScriptContents")
    public static final BasicConfigKey<String> CREATION_SCRIPT_CONTENTS = new BasicConfigKey<String>(String.class, "mysql.creation.script.contents", "MySQL creation script (SQL contents)", "");

    @SetFromFlag("creationScriptUrl")
    public static final BasicConfigKey<String> CREATION_SCRIPT_URL = new BasicConfigKey<String>(String.class, "mysql.creation.script.url", "URL where MySQL creation script can be found", "");

    @SetFromFlag("dataDir")
    public static final ConfigKey<String> DATA_DIR = new BasicConfigKey<String>(
            String.class, "mysql.datadir", "Directory for writing data files", null);

    public static final MapConfigKey<Object> MYSQL_SERVER_CONF = new MapConfigKey<Object>(Object.class, "mysql.server.conf", "Configuration options for mysqld");
    
    public static final ConfigKey<Object> MYSQL_SERVER_CONF_LOWER_CASE_TABLE_NAMES = MYSQL_SERVER_CONF.subKey("lower_case_table_names", "See MySQL guide. Set 1 to ignore case in table names (useful for OS portability)");
    
    /** download mirror, if desired; defaults to Austria which seems one of the fastest */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "mysql.install.mirror.url", "URL of mirror", 
//        "http://mysql.mirrors.pair.com/"   // Pennsylvania
//        "http://gd.tuwien.ac.at/db/mysql/"
        "http://www.mirrorservice.org/sites/ftp.mysql.com/" //UK mirror service
         );

    public static final BasicAttributeSensor<String> MYSQL_URL = new BasicAttributeSensor<String>(String.class, "mysql.url", "URL to access mysql (e.g. mysql://localhost:3306/)");
}
