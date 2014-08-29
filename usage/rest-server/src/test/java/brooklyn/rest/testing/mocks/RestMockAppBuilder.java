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
package brooklyn.rest.testing.mocks;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.util.javalang.Reflections;

public class RestMockAppBuilder extends ApplicationBuilder {

    public RestMockAppBuilder() {
        super(EntitySpec.create(StartableApplication.class).impl(RestMockApp.class));
    }
    
    @Override
    protected void doBuild() {
        addChild(EntitySpec.create(Entity.class).impl(RestMockSimpleEntity.class)
            .additionalInterfaces(Reflections.getAllInterfaces(RestMockSimpleEntity.class))
            .displayName("child1"));
    }
}
