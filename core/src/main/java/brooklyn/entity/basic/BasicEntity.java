package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;

public class BasicEntity extends AbstractEntity {

    public BasicEntity() {
        super();
    }
    
    public BasicEntity(Entity owner) {
        super(owner);
    }

    public BasicEntity(Map<?,?> flags) {
        super(flags);
    }
    
    public BasicEntity(Map<?,?> flags, Entity owner) {
        super(flags, owner);
    }
}
