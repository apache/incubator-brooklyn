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
package brooklyn.rest.util;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;

import com.google.common.collect.ImmutableList;

public class EntityLocationUtilsTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(EntityLocationUtilsTest.class);
    
    private Location loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationRegistry().resolve("localhost");
        ((AbstractLocation)loc).setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));
    }
    
    @Test
    public void testCount() {
        @SuppressWarnings("unused")
        SoftwareProcess r1 = app.createAndManageChild(EntitySpec.create(SoftwareProcess.class, RestMockSimpleEntity.class));
        SoftwareProcess r2 = app.createAndManageChild(EntitySpec.create(SoftwareProcess.class, RestMockSimpleEntity.class));
        Entities.start(app, Arrays.<Location>asList(loc));

        Entities.dumpInfo(app);

        log.info("r2loc: "+r2.getLocations());
        log.info("props: "+r2.getLocations().iterator().next().getAllConfig(false));

        Map<Location, Integer> counts = new EntityLocationUtils(mgmt).countLeafEntitiesByLocatedLocations();
        log.info("count: "+counts);
        assertEquals(ImmutableList.copyOf(counts.values()), ImmutableList.of(2), "counts="+counts);
    }
}
