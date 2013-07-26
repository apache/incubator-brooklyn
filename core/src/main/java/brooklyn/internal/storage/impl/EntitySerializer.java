package brooklyn.internal.storage.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import brooklyn.entity.Entity;
import brooklyn.internal.storage.Serializer;
import brooklyn.internal.storage.impl.EntitySerializer.EntityPointer;
import brooklyn.management.internal.LocalEntityManager;
import brooklyn.management.internal.ManagementContextInternal;

import com.google.common.base.Objects;

public class EntitySerializer implements Serializer<Entity, EntityPointer> {

    public static class EntityPointer implements Serializable {
        private static final long serialVersionUID = -3358568001462543001L;
        
        final String id;
        
        public EntityPointer(String id) {
            this.id = checkNotNull(id, "id");
        }
        @Override public String toString() {
            return Objects.toStringHelper(this).add("id",  id).toString();
        }
        @Override public int hashCode() {
            return id.hashCode();
        }
        @Override public boolean equals(Object obj) {
            return (obj instanceof EntityPointer) && id.equals(((EntityPointer)obj).id);
        }
    }

    private final ManagementContextInternal managementContext;
    
    public EntitySerializer(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    @Override
    public EntityPointer serialize(Entity orig) {
        return new EntityPointer(orig.getId());
    }

    @Override
    public Entity deserialize(EntityPointer serializedForm) {
        Entity result = ((LocalEntityManager)managementContext.getEntityManager()).getEntityEvenIfPreManaged(serializedForm.id);
        return checkNotNull(result, "no entity with id %s", serializedForm.id);
    }
}
