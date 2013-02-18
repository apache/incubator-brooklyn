package brooklyn.entity.database;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

public interface DatabaseNode extends SoftwareProcess {
    public static final AttributeSensor<String> DB_URL = new BasicAttributeSensor<String>(String.class, "database.url",
            "URL where database is listening");

}
