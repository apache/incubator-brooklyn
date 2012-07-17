package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class JBoss6ServerFactory extends BasicConfigurableEntityFactory<JBoss6Server> {
    public JBoss6ServerFactory(){
        this(new LinkedHashMap());
    }

    public JBoss6ServerFactory(Map flags) {
        super(flags, JBoss6Server.class);
    }
}