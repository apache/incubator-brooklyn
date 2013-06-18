package brooklyn.entity.database.mysql;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="MySql Node", description="MySql is an open source relational database management system (RDBMS)", iconUrl="classpath:///mysql-logo-110x57.png")
@ImplementedBy(MySqlNodeImpl.class)
public interface MySqlNode extends DatabaseNode, HasShortName {

    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.5.30");

    //http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-5.5.21-osx10.6-x86_64.tar.gz/from/http://gd.tuwien.ac.at/db/mysql/
    //http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-5.5.21-linux2.6-i686.tar.gz/from/http://gd.tuwien.ac.at/db/mysql/
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-${version}-${driver.osTag}.tar.gz/from/${driver.mirrorUrl}");

    /** download mirror, if desired; defaults to Austria which seems one of the fastest */
    @SetFromFlag("mirrorUrl")
    public static final ConfigKey<String> MIRROR_URL = ConfigKeys.newStringConfigKey("mysql.install.mirror.url", "URL of mirror", 
//        "http://mysql.mirrors.pair.com/"   // Pennsylvania
//        "http://gd.tuwien.ac.at/db/mysql/"
        "http://www.mirrorservice.org/sites/ftp.mysql.com/" //UK mirror service
         );

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MYSQL_PORT = new PortAttributeSensorAndConfigKey("mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+"));

    @SetFromFlag("creationScriptContents")
    public static final ConfigKey<String> CREATION_SCRIPT_CONTENTS = ConfigKeys.newStringConfigKey("mysql.creation.script.contents", "MySQL creation script (SQL contents)", "");

    @SetFromFlag("creationScriptUrl")
    public static final ConfigKey<String> CREATION_SCRIPT_URL = ConfigKeys.newStringConfigKey("mysql.creation.script.url", "URL where MySQL creation script can be found", "");

    @SetFromFlag("dataDir")
    public static final ConfigKey<String> DATA_DIR = ConfigKeys.newStringConfigKey(
            "mysql.datadir", "Directory for writing data files", null);

    @SetFromFlag("serverConf")
    public static final MapConfigKey<Object> MYSQL_SERVER_CONF = new MapConfigKey<Object>(
            Object.class, "mysql.server.conf", "Configuration options for mysqld");
    
    public static final ConfigKey<Object> MYSQL_SERVER_CONF_LOWER_CASE_TABLE_NAMES = MYSQL_SERVER_CONF.subKey("lower_case_table_names", "See MySQL guide. Set 1 to ignore case in table names (useful for OS portability)");
    
    @SetFromFlag("password")
    public static final StringAttributeSensorAndConfigKey PASSWORD = new StringAttributeSensorAndConfigKey(
            "mysql.password", "Database admin password (or randomly generated if not set)", null);

    @SetFromFlag("socketUid")
    public static final StringAttributeSensorAndConfigKey SOCKET_UID = new StringAttributeSensorAndConfigKey(
            "mysql.socketUid", "Socket uid, for use in file /tmp/mysql.sock.<uid>.3306 (or randomly generated if not set)", null);
    
    public static final AttributeSensor<String> MYSQL_URL = DB_URL;

    @SetFromFlag("configurationTemplateUrl")
    static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new StringAttributeSensorAndConfigKey(
            "mysql.template.configuration.url", "Template file (in freemarker format) for the mysql.conf file",
            "classpath://brooklyn/entity/database/mysql/mysql.conf");

}
