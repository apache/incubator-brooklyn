package brooklyn.entity.webapp.tomcat;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class TomcatServerFactory extends BasicConfigurableEntityFactory<TomcatServer> {
    public TomcatServerFactory() {
        this(new LinkedHashMap());
    }

    public TomcatServerFactory(Map flags) {
        super(flags, TomcatServer.class);
    }
}
