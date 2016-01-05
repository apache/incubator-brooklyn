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
package org.apache.brooklyn.test.osgi.entities.more;

import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.core.config.ConfigBag;

public class MoreEntityImpl extends AbstractEntity implements MoreEntity {

    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(SAY_HI, new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return sayHI((String)parameters.getStringKey("name"));
            }
        });
    }
    
    @Override
    public String sayHI(String name) {
        return "Hi "+name.toUpperCase()+" from V1";
    }
    
}
