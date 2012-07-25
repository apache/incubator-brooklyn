package brooklyn.entity.webapp.jboss;


import brooklyn.entity.basic.BasicConfigurableEntityFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class JBoss7ServerFactory extends BasicConfigurableEntityFactory<JBoss7Server> {
    public JBoss7ServerFactory(){
        this(new LinkedHashMap());
    }

    public JBoss7ServerFactory(Map flags) {
        super(flags, JBoss7Server.class);
    }
}
