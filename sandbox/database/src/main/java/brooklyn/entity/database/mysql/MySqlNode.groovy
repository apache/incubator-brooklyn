package brooklyn.entity.database.mysql

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopDriver
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

public class MySqlNode extends SoftwareProcessEntity {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [SoftwareProcessEntity.SUGGESTED_VERSION, "5.5.21"]

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
    
}
