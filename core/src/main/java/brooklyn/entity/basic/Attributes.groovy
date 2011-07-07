package brooklyn.entity.basic

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey;

class Attributes {

    public static final BasicAttributeSensor<Integer> JMX_PORT = [ Integer, "jmx.port", "JMX port" ]
    public static final BasicAttributeSensor<String> JMX_HOST = [ String, "jmx.host", "JMX host" ]
 
    public static final BasicAttributeSensor<Integer> HTTP_PORT = [ Integer, "http.port", "HTTP port" ]
}
