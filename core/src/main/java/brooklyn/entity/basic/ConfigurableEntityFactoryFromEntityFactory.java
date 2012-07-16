package brooklyn.entity.basic;

import brooklyn.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class ConfigurableEntityFactoryFromEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {

   private final EntityFactory<T> factory;

    public ConfigurableEntityFactoryFromEntityFactory(EntityFactory<T> entityFactory){
        this(new HashMap(),entityFactory);
    }

    public ConfigurableEntityFactoryFromEntityFactory(Map flags, EntityFactory<T> factory) {
        super(flags);
        this.factory = factory;
    }

    @Override
    public T newEntity2(Map flags, Entity owner) {
        return factory.newEntity(flags, owner);
    }
}