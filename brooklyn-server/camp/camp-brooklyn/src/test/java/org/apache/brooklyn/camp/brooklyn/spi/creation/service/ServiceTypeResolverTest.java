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
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.testng.annotations.Test;


public class ServiceTypeResolverTest extends AbstractYamlTest {
    
    @Test
    public void testAddCatalogItemVerySimple() throws Exception {
        EntitySpec<?> spec = createAppEntitySpec(
                "services:",
                "- type: \"test-resolver:" + BasicEntity.class.getName() + "\"");
        assertEquals(spec.getChildren().get(0).getFlags().get("resolver"), TestServiceTypeResolver.class);
    }

}
