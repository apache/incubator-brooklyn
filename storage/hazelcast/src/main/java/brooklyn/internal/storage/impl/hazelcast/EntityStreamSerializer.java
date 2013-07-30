package brooklyn.internal.storage.impl.hazelcast;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntityProxyImpl;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;

import static java.lang.String.format;

class EntityStreamSerializer implements StreamSerializer {

    private HazelcastDataGrid hazelcastDataGrid;

    public EntityStreamSerializer(HazelcastDataGrid hazelcastDataGrid) {
        this.hazelcastDataGrid = hazelcastDataGrid;
    }

    @Override
    public Object read(ObjectDataInput in) throws IOException {
        EntityId id = in.readObject();
        Entity entity = hazelcastDataGrid.getManagementContext().getEntityManager().getEntity(id.getId());
        if (entity == null) {
            throw new IllegalStateException(format("Entity with id [%s] is not found", id));
        }
        return java.lang.reflect.Proxy.newProxyInstance(
                entity.getClass().getClassLoader(),
                entity.getClass().getInterfaces(),
                new EntityProxyImpl(entity));
    }

    @Override
    public void write(ObjectDataOutput out, Object object) throws IOException {
        Entity entity = (Entity) object;
        out.writeObject(new EntityId(entity.getId()));
    }

    @Override
    public int getTypeId() {
        return 5000;
    }

    @Override
    public void destroy() {
        //no-op
    }
}
