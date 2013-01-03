package brooklyn.event.adapter;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.util.internal.TimeExtras;

/**
 * @deprecated Use brooklyn.event.feed.jmx.JmxHelper instead
 */
@Deprecated
public class JmxHelper extends brooklyn.event.feed.jmx.JmxHelper {

    static { TimeExtras.init(); }

    public JmxHelper(EntityLocal entity) {
        super(entity);
    }
    
    public JmxHelper(String url) {
        super(url);
    }

    public JmxHelper(String url, String user, String password) {
        super(url, user, password);
    }
    
    public JmxHelper(String url, EntityLocal entity, String user, String password) {
        super(url, entity, user, password);
    }
}
