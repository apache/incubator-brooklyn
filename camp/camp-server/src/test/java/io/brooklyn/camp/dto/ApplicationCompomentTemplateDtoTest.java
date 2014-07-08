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
package io.brooklyn.camp.dto;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApplicationCompomentTemplateDtoTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCompomentTemplateDtoTest.class);
    
    @Test
    public void testAppServerPct() {
        CampPlatform p = MockWebPlatform.newPlatform();
        DtoFactory f = new DtoFactory(p, "");
        
        ApplicationComponentTemplate t = MockWebPlatform.WAR;
        ApplicationComponentTemplateDto dto = f.adapt(t);
        
        log.info("War PCT serialized as: "+BasicDtoTest.tree(dto));
        Assert.assertEquals(dto.getName(), t.getName());
        Assert.assertNotNull(dto.getCreatedAsString());
        Assert.assertTrue(dto.getCreatedAsString().startsWith("20"));
    }
    
}
