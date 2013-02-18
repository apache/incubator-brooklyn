package brooklyn.entity.database.rubyrep;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="RubyRep Node", description="RubyRep is a database replication system", iconUrl="classpath:///rubyrep-logo.jpeg")
@ImplementedBy(RubyRepNodeImpl.class)
public interface RubyRepNode extends SoftwareProcess, EntityLocal {
    @SetFromFlag("version")
    static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "1.2.0");

    @SetFromFlag("configurationScriptUrl")
    static final BasicAttributeSensorAndConfigKey<String> CONFIGURATION_SCRIPT_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.config.script.url", "URL where RubyRep configuration can be found", null);

    @SetFromFlag("configurationScriptContents")
    static final BasicAttributeSensorAndConfigKey<String> CONFIGURATION_CONTENTS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.config.script", "RubyRep configuration script contents", null);

    @SetFromFlag("tables")
    static final BasicAttributeSensorAndConfigKey<String> TABLE_REGEXP =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.table.regex", "Regular expression to select tables to sync using RubyRep", ".");

    @SetFromFlag("leftUrl")
    static final BasicAttributeSensorAndConfigKey<String> LEFT_DATABASE_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.left.database.url", "Left database URL", null);

    @SetFromFlag("replicationInterval")
    static final BasicAttributeSensorAndConfigKey<Integer> REPLICATION_INTERVAL =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "rubyrep.replicationInterval", "Replication Interval", 30);

    @SetFromFlag("leftDatabase")
    static final BasicConfigKey<String> LEFT_DATABASE = new BasicConfigKey.StringConfigKey("rubyrep.left.database");

    @SetFromFlag("leftUsername")
    static final BasicConfigKey<String> LEFT_USERNAME = new BasicConfigKey.StringConfigKey("rubyrep.left.username");

    @SetFromFlag("leftPassword")
    static final BasicConfigKey<String> LEFT_PASSWORD =  new BasicConfigKey.StringConfigKey("rubyrep.left.password");

    @SetFromFlag("rightUrl")
    static final BasicAttributeSensorAndConfigKey<String> RIGHT_DATABASE_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.right.database.url", "Right database URL", null);

    @SetFromFlag("rightDatabase")
    static final BasicConfigKey<String> RIGHT_DATABASE = new BasicConfigKey.StringConfigKey("rubyrep.right.database");

    @SetFromFlag("rightUsername")
    static final BasicConfigKey<String> RIGHT_USERNAME = new BasicConfigKey.StringConfigKey("rubyrep.right.username");

    @SetFromFlag("rightPassword")
    static final BasicConfigKey<String> RIGHT_PASSWORD = new BasicConfigKey.StringConfigKey("rubyrep.right.password");

}
