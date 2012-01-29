package brooklyn.entity.basic;

import java.util.Map

import brooklyn.entity.Entity

public class BasicEntity extends AbstractEntity {
    
    public BasicEntity(Entity owner) {
        super(owner)
    }

    public BasicEntity(Map flags=null, Entity owner=null) {
        super(flags, owner);
    }
}
