package io.brooklyn.camp.brooklyn;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicConfigKey;

public class TestReferencingEnricher extends AbstractEnricher {
    public static final ConfigKey<Entity> TEST_APPLICATION = new BasicConfigKey<Entity>(Entity.class, "test.reference.app");
    public static final ConfigKey<Entity> TEST_ENTITY_1 = new BasicConfigKey<Entity>(Entity.class, "test.reference.entity1");
    public static final ConfigKey<Entity> TEST_ENTITY_2 = new BasicConfigKey<Entity>(Entity.class, "test.reference.entity2");
    public static final ConfigKey<Entity> TEST_CHILD_1 = new BasicConfigKey<Entity>(Entity.class, "test.reference.child1");
    public static final ConfigKey<Entity> TEST_CHILD_2 = new BasicConfigKey<Entity>(Entity.class, "test.reference.child2");
    public static final ConfigKey<Entity> TEST_GRANDCHILD_1 = new BasicConfigKey<Entity>(Entity.class, "test.reference.grandchild1");
    public static final ConfigKey<Entity> TEST_GRANDCHILD_2 = new BasicConfigKey<Entity>(Entity.class, "test.reference.grandchild2");
}
