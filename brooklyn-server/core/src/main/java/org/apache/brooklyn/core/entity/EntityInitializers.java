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
package org.apache.brooklyn.core.entity;

import java.util.List;

import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntityLocal;

import com.google.common.collect.ImmutableList;

public class EntityInitializers {

    public static class AddTags implements EntityInitializer {
        public final List<Object> tags;
        
        public AddTags(Object... tags) {
            this.tags = ImmutableList.copyOf(tags);
        }
        
        @Override
        public void apply(EntityLocal entity) {
            for (Object tag: tags)
                entity.tags().addTag(tag);
        }
    }

    
    public static EntityInitializer addingTags(Object... tags) {
        return new AddTags(tags);
    }
    
}
