package brooklyn.internal.storage.impl.hazelcast;

import java.io.Serializable;

class EntityId implements Serializable {
    private final String id;

    EntityId(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }
}
