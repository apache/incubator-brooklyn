package io.brooklyn.camp.brooklyn;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.reflect.TypeToken;

@ImplementedBy(TestEntityWithInitConfigImpl.class)
public interface TestEntityWithInitConfig extends Entity, Startable, EntityLocal, EntityInternal {
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_ENTITY = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.entity")
            .build();
    public Entity getEntityCachedOnInit();
}
