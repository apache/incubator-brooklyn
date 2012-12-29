package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;
import com.google.common.collect.Maps;

public class BasicEntity extends AbstractEntity {
    
    public BasicEntity() {
        super(Maps.newLinkedHashMap(), null);
    }

    public BasicEntity(Entity parent) {
        super(Maps.newLinkedHashMap(), parent);
    }

    public BasicEntity(Map flags) {
        this(flags, null);
    }
    
    public BasicEntity(Map flags, Entity parent) {
        super(flags, parent);
    }
}
