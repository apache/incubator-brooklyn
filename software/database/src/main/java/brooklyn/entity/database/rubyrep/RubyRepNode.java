package brooklyn.entity.database.rubyrep;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name = "RubyRep Node", description = "RubyRep is a database replication system", iconUrl = "classpath:///rubyrep-logo.jpeg")
@ImplementedBy(RubyRepNodeImpl.class)
public interface RubyRepNode extends SoftwareProcess {
    // TODO see RubyRepSshDriver#install()
    @SetFromFlag("version")
    static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.0");

    @SetFromFlag("configurationScriptUrl")
    static final ConfigKey<String> CONFIGURATION_SCRIPT_URL = new BasicConfigKey<String>(String.class, "database.rubyrep.configScriptUrl",
            "URL where RubyRep configuration can be found - disables other configuration options (except version)");

    @SetFromFlag("templateUrl")
    static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "database.rubyrep.templateConfigurationUrl", "Template file (in freemarker format) for the rubyrep.conf file",
            "classpath://brooklyn/entity/database/rubyrep/rubyrep.conf");

    @SetFromFlag("tables")
    static final ConfigKey<String> TABLE_REGEXP = new BasicConfigKey<String>(
            String.class, "database.rubyrep.tableRegex", "Regular expression to select tables to sync using RubyRep", ".");

    @SetFromFlag("replicationInterval")
    static final ConfigKey<Integer> REPLICATION_INTERVAL = new BasicConfigKey<Integer>(
            Integer.class, "database.rubyrep.replicationInterval", "Replication Interval", 30);

    @SetFromFlag("leftUrl")
    static final BasicAttributeSensorAndConfigKey<String> LEFT_DATABASE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "database.rubyrep.leftDatabaseUrl", "URL of the left database");

    @SetFromFlag("leftDatabase")
    static final ConfigKey<? extends DatabaseNode> LEFT_DATABASE = new BasicConfigKey<DatabaseNode>(
            DatabaseNode.class, "database.rubyrep.leftDatabase", "Brooklyn database entity to use as the left DBMS");

    @SetFromFlag("leftDatabaseName")
    static final ConfigKey<String> LEFT_DATABASE_NAME = new BasicConfigKey<String>(
            String.class, "database.rubyrep.leftDatabaseName", "name of database to use for left db");

    @SetFromFlag("leftUsername")
    static final ConfigKey<String> LEFT_USERNAME = new BasicConfigKey<String>(
            String.class, "database.rubyrep.leftUsername", "username to connect to left db");

    @SetFromFlag("leftPassword")
    static final ConfigKey<String> LEFT_PASSWORD = new BasicConfigKey<String>(
            String.class, "database.rubyrep.leftPassword", "password to connect to left db");

    @SetFromFlag("rightUrl")
    static final BasicAttributeSensorAndConfigKey<String> RIGHT_DATABASE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "database.rubyrep.rightDatabaseUrl", "Right database URL");

    @SetFromFlag("rightDatabase")
    static final ConfigKey<? extends DatabaseNode> RIGHT_DATABASE = new BasicConfigKey<DatabaseNode>(
            DatabaseNode.class, "database.rubyrep.rightDatabase");

    @SetFromFlag("rightDatabaseName")
    static final ConfigKey<String> RIGHT_DATABASE_NAME = new BasicConfigKey<String>(
            String.class, "database.rubyrep.rightDatabaseName", "name of database to use for left db");

    @SetFromFlag("rightUsername")
    static final ConfigKey<String> RIGHT_USERNAME = new BasicConfigKey<String>(
            String.class, "database.rubyrep.rightUsername", "username to connect to right db");

    @SetFromFlag("rightPassword")
    static final ConfigKey<String> RIGHT_PASSWORD = new BasicConfigKey<String>(
            String.class, "database.rubyrep.rightPassword", "password to connect to right db");

}
