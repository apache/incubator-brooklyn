/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
