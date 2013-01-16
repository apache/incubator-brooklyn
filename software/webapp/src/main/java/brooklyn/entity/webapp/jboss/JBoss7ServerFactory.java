package brooklyn.entity.webapp.jboss;


import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;

public class JBoss7ServerFactory extends BasicConfigurableEntityFactory<JBoss7Server> {
    public JBoss7ServerFactory(){
        this(new LinkedHashMap());
    }

    public JBoss7ServerFactory(Map flags) {
        super(flags, JBoss7ServerImpl.class);
    }
}
