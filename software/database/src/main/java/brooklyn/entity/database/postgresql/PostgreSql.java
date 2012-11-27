package brooklyn.entity.database.postgresql;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

public interface PostgreSql extends Entity {

    public static final AttributeSensor<String> DB_URL = new BasicAttributeSensor<String>(String.class, "database.url", 
            "URL where database is listening");

}
