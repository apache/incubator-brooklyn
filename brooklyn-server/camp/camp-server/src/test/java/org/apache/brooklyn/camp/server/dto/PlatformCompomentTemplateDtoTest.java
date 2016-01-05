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
package org.apache.brooklyn.camp.server.dto;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.server.dto.PlatformComponentTemplateDto;
import org.apache.brooklyn.camp.server.rest.util.DtoFactory;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.camp.test.mock.web.MockWebPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PlatformCompomentTemplateDtoTest {

    private static final Logger log = LoggerFactory.getLogger(PlatformCompomentTemplateDtoTest.class);
    
    @Test
    public void testAppServerPct() {
        CampPlatform p = MockWebPlatform.newPlatform();
        DtoFactory f = new DtoFactory(p, "");
        
        PlatformComponentTemplate t = MockWebPlatform.APPSERVER;
        PlatformComponentTemplateDto dto = f.adapt(t);
        
        log.info("Web PCT serialized as: "+BasicDtoTest.tree(dto));
        Assert.assertEquals(dto.getName(), t.getName());
        Assert.assertNotNull(dto.getCreatedAsString());
        Assert.assertTrue(dto.getCreatedAsString().startsWith("20"));
    }
    
}
