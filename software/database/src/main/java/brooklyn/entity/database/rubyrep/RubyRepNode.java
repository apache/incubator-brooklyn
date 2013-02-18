package brooklyn.entity.database.rubyrep;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface RubyRepNode extends SoftwareProcess, EntityLocal {
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "1.2.0");

    @SetFromFlag("configurationScriptUrl")
    public static final BasicAttributeSensorAndConfigKey<String> CONFIGURATION_SCRIPT_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.config.script.url", "URL where RubyRep configuration can be found", null);

    @SetFromFlag("configurationScriptContents")
    public static final BasicAttributeSensorAndConfigKey<String> CONFIGURATION_CONTENTS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.config.script", "RubyRep configuration script contents", null);

    @SetFromFlag("tables")
    public static final BasicAttributeSensorAndConfigKey<String> TABLE_REGEXP =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.table.regex", "Regular expression to select tables to sync using RubyRep", ".");

    @SetFromFlag("leftUrl")
    public static final BasicAttributeSensorAndConfigKey<String> LEFT_DATABASE_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.left.database.url", "Left database URL", null);

    @SetFromFlag("replicationInterval")
    public static final BasicAttributeSensorAndConfigKey<Integer> REPLICATION_INTERVAL =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "rubyrep.replicationInterval", "Replication Interval", 30);

    @SetFromFlag("leftDatabase")
    public static final BasicConfigKey<String> LEFT_DATABASE = new BasicConfigKey.StringConfigKey("rubyrep.left.database");

    @SetFromFlag("leftUsername")
    public static final BasicConfigKey<String> LEFT_USERNAME = new BasicConfigKey.StringConfigKey("rubyrep.left.username");

    @SetFromFlag("leftPassword")
    public static final BasicConfigKey<String> LEFT_PASSWORD =  new BasicConfigKey.StringConfigKey("rubyrep.left.password");

    @SetFromFlag("rightUrl")
    public static final BasicAttributeSensorAndConfigKey<String> RIGHT_DATABASE_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "rubyrep.right.database.url", "Right database URL", null);

    @SetFromFlag("rightDatabase")
    public static final BasicConfigKey<String> RIGHT_DATABASE = new BasicConfigKey.StringConfigKey("rubyrep.right.database");

    @SetFromFlag("rightUsername")
    public static final BasicConfigKey<String> RIGHT_USERNAME = new BasicConfigKey.StringConfigKey("rubyrep.right.username");

    @SetFromFlag("rightPassword")
    public static final BasicConfigKey<String> RIGHT_PASSWORD = new BasicConfigKey.StringConfigKey("rubyrep.right.password");

}
