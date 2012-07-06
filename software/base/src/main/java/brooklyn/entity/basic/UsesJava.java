package brooklyn.entity.basic;

import brooklyn.event.basic.BasicConfigKey;

import java.util.HashMap;
import java.util.Map;

public interface UsesJava {

    public static final BasicConfigKey<Map<String, String>> JAVA_OPTIONS = new BasicConfigKey<Map<String, String>>(
            (Class)Map.class, "java.options", "Java command line options", new HashMap<String,String>());
}

