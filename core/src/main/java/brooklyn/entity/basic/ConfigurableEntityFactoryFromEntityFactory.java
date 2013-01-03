package brooklyn.entity.basic;

import brooklyn.entity.Entity;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigurableEntityFactoryFromEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {

   private final EntityFactory<? extends T> factory;

    public ConfigurableEntityFactoryFromEntityFactory(EntityFactory<? extends T> entityFactory){
        this(new HashMap(),entityFactory);
    }

    public ConfigurableEntityFactoryFromEntityFactory(Map flags, EntityFactory<? extends T> factory) {
        super(flags);
        this.factory = checkNotNull(factory, "factory");
    }

    @Override
    public T newEntity2(Map flags, Entity parent) {
        return factory.newEntity(flags, parent);
    }
}