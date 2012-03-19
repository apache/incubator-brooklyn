package brooklyn.entity.database.mysql

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopDriver
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.PortRanges
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

public class MySqlNode extends SoftwareProcessEntity {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [SoftwareProcessEntity.SUGGESTED_VERSION, "5.5.21"]

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MYSQL_PORT = [ "mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+") ]

    @SetFromFlag("creationScript")
    public static final BasicConfigKey<String> CREATION_SCRIPT = [ String, "mysql.creation.script", "MySQL creation script", "" ]

    public MySqlNode(Entity owner) { this([:], owner) }
    public MySqlNode(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }

    @Override
    protected StartStopDriver newDriver(SshMachineLocation loc) {
        return new MySqlSshDriver(this, loc);
    }

    protected void connectSensors() {
        super.connectSensors();
        //TODO sensors
    }

    public int getPort() {
        getAttribute(MYSQL_PORT)
    }

}
