package brooklyn.rest.mock;

import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.basic.AbstractApplication;

public class RestMockApp extends AbstractApplication {
    
    public RestMockApp(){
        this(new LinkedHashMap());
    }

    public RestMockApp(Map properties) {
        super(properties);
    }

}
