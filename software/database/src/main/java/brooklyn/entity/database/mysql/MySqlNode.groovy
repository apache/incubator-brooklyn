package brooklyn.entity.database.mysql

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.StartStopDriver
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.Location
import brooklyn.location.basic.PortRanges
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation
import brooklyn.util.flags.SetFromFlag

public class MySqlNode extends SoftwareProcessEntity {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [SoftwareProcessEntity.SUGGESTED_VERSION, "5.5.21"]

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey MYSQL_PORT = [ "mysql.port", "MySQL port", PortRanges.fromString("3306, 13306+") ]

    @SetFromFlag("creationScript")
    public static final BasicConfigKey<String> CREATION_SCRIPT = [ String, "mysql.creation.script", "MySQL creation script", "" ]

	@SetFromFlag("mirrorUrl")
	public static final BasicConfigKey<String> MIRROR_URL = [ String, "mysql.install.mirror.url", "URL of mirror", 
//		"http://mysql.mirrors.pair.com/"
		"http://gd.tuwien.ac.at/db/mysql/"
		 ]

    public static final BasicAttributeSensor<String> MYSQL_URL = [ String, "mysql.url", "URL to access mysql (e.g. mysql://localhost:3306/)" ]
    
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
        Location l = locations.iterator().next();
        String hostname = null;
        if (l in JcloudsSshMachineLocation) hostname = ((JcloudsSshMachineLocation)l).getSubnetHostname();
        else if (l in SshMachineLocation) hostname =  ((SshMachineLocation)l).getAddress()?.hostAddress;
        if (hostname==null) 
            throw new IllegalStateException("Cannot find hostname for unexpected location type "+l+" where "+this+" is running");
        setAttribute(MYSQL_URL, "mysql://"+hostname+":"+getAttribute(MYSQL_PORT)+"/");
    }

    public int getPort() {
        getAttribute(MYSQL_PORT)
    }

}
