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
package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.PlatformComponentTemplateDto;
import io.brooklyn.camp.dto.PlatformDto;
import io.brooklyn.camp.test.fixture.AbstractRestResourceTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PlatformRestResourceTest extends AbstractRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(PlatformRestResourceTest.class);
    
    @Test
    public void testPlatformIncludesList() {
        PlatformDto p = load(PlatformRestResource.CAMP_URI_PATH, PlatformDto.class);
        PlatformComponentTemplateDto pct = load(p.getPlatformComponentTemplates().get(0).getHref(), PlatformComponentTemplateDto.class);
        log.debug("Loaded PCT via REST: "+pct);
        Assert.assertNotNull(pct.getName());
    }
        
}
