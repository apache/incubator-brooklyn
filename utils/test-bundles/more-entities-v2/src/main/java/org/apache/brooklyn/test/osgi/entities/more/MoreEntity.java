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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.effector.core.Effectors;

@Catalog(name="More Entity v2")
@ImplementedBy(MoreEntityImpl.class)
public interface MoreEntity extends Entity {

    public static final Effector<String> SAY_HI = Effectors.effector(String.class, "sayHI")
        .description("says HI to an uppercased name")
        .parameter(String.class, "name")
        .buildAbstract();

    /** Makes a string saying hi to the given name, in uppercase, for testing. 
     * In contrast to v1, impl here returns HI not Hi. */
    String sayHI(String name);
    
}
