package brooklyn.util.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group

public class EntityNavigationUtils {
    static final Logger log = LoggerFactory.getLogger(EntityNavigationUtils.class)
 
    public static void dump(Entity e, String prefix="") {
        if (log.isDebugEnabled()) log.debug prefix+e
        if (e in Group) {
            e.children.each { dump it, prefix+"  " }
        }
    } 
    
}
