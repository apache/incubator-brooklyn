package io.brooklyn.camp.brooklyn;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.test.entity.TestApplicationImpl;

@ImplementedBy(ReferencingYamlTestEntityImpl.class)
public interface ReferencingYamlTestEntity {
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_APP = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.app")
            .build();
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_ENTITY1 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.entity1")
            .build();    
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_ENTITY2 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.entity2")
            .build();
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_CHILD1 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.child1")
            .build();
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_CHILD2 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.child2")
            .build(); 
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_GRANDCHILD1 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.grandchild1")
            .build();
    @SuppressWarnings("serial")
    public static final ConfigKey<Entity> TEST_REFERENCE_GRANDCHILD2 = BasicConfigKey.builder(new TypeToken<Entity>(){})
            .name("test.reference.grandchild2")
            .build(); 
}
