package brooklyn.entity.database;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

public interface DatabaseNode extends SoftwareProcess {
    public static final AttributeSensor<String> DB_URL = Sensors.newStringSensor("database.url",
            "URL where database is listening (e.g. mysql://localhost:3306/)");

}
