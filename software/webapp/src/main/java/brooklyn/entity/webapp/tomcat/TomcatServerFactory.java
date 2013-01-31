package brooklyn.entity.webapp.tomcat;

import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;

/**
 * @deprecated since 0.5; instead use new BasicConfigurableEntityFactory(TomcatServerImpl.class)
 */
@Deprecated
public class TomcatServerFactory extends BasicConfigurableEntityFactory<TomcatServerImpl> {
    public TomcatServerFactory() {
        this(new LinkedHashMap());
    }

    public TomcatServerFactory(Map flags) {
        super(flags, TomcatServerImpl.class);
    }
}
