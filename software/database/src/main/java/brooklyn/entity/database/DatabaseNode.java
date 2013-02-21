package brooklyn.entity.database;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensor;

public interface DatabaseNode extends SoftwareProcess {
    public static final BasicAttributeSensor<String> DB_URL = new BasicAttributeSensor<String>(String.class, "database.url",
            "URL where database is listening (e.g. mysql://localhost:3306/)");

}
