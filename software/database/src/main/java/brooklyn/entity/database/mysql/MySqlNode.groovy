package brooklyn.entity.database.mysql

import brooklyn.config.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.MapConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.PortRanges
import brooklyn.util.flags.SetFromFlag

public class MySqlNode extends SoftwareProcessEntity {

    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "5.5.29" ]

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MYSQL_PORT = [ "mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+") ]

    @SetFromFlag("creationScriptContents")
    public static final BasicConfigKey<String> CREATION_SCRIPT_CONTENTS = [ String, "mysql.creation.script.contents", "MySQL creation script (SQL contents)", "" ]

    @SetFromFlag("creationScriptUrl")
    public static final BasicConfigKey<String> CREATION_SCRIPT_URL = [ String, "mysql.creation.script.url", "URL where MySQL creation script can be found", "" ]

    @SetFromFlag("dataDir")
    public static final ConfigKey<String> DATA_DIR = new BasicConfigKey<String>(
            String.class, "mysql.datadir", "Directory for writing data files", null);
    
    public static final MapConfigKey<String> MYSQL_SERVER_CONF = [ Object, "mysql.server.conf", "Configuration options for mysqld" ]
    public static final ConfigKey<Object> MYSQL_SERVER_CONF_LOWER_CASE_TABLE_NAMES = MYSQL_SERVER_CONF.subKey("lower_case_table_names", "See MySQL guide. Set 1 to ignore case in table names (useful for OS portability)");
    
    /** download mirror, if desired; defaults to Austria which seems one of the fastest */
	@SetFromFlag("mirrorUrl")
	public static final BasicConfigKey<String> MIRROR_URL = [ String, "mysql.install.mirror.url", "URL of mirror", 
//		"http://mysql.mirrors.pair.com/"   // Pennsylvania
//		"http://gd.tuwien.ac.at/db/mysql/"
        "http://www.mirrorservice.org/sites/ftp.mysql.com/" //UK mirror service
		 ]

    public static final BasicAttributeSensor<String> MYSQL_URL = [ String, "mysql.url", "URL to access mysql (e.g. mysql://localhost:3306/)" ]

    public MySqlNode(Entity parent) {
        this([:], parent)
    }

    public MySqlNode(Map flags=[:], Entity parent=null) {
        super(flags, parent)
    }

    @Override
    public Class getDriverInterface() {
        return MySqlDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(MYSQL_URL, "mysql://${localHostname}:${port}/")
        setAttribute(SERVICE_UP, true)  // TODO poll for status, and activity
    }

    public int getPort() {
        getAttribute(MYSQL_PORT)
    }
}
