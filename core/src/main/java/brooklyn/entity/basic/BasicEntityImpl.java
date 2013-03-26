package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;

import com.google.common.collect.Maps;

public class BasicEntityImpl extends AbstractEntity implements BasicEntity {
    
    public BasicEntityImpl() {
        super(Maps.newLinkedHashMap(), null);
    }

    public BasicEntityImpl(Entity parent) {
        super(Maps.newLinkedHashMap(), parent);
    }

    public BasicEntityImpl(Map flags) {
        this(flags, null);
    }
    
    public BasicEntityImpl(Map flags, Entity parent) {
        super(flags, parent);
    }
}
