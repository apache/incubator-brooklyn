package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;

import com.google.common.collect.Maps;

public class BasicEntity extends AbstractEntity {
    
    public BasicEntity() {
        super(Maps.newLinkedHashMap(), null);
    }

    public BasicEntity(Entity owner) {
        super(Maps.newLinkedHashMap(), owner);
    }

    public BasicEntity(Map flags) {
        this(flags, null);
    }
    
    public BasicEntity(Map flags, Entity owner) {
        super(flags, owner);
    }
}
