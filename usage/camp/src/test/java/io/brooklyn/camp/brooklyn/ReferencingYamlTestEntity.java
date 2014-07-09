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
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.reflect.TypeToken;

@ImplementedBy(ReferencingYamlTestEntityImpl.class)
public interface ReferencingYamlTestEntity extends Entity {
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
