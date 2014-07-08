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
package brooklyn.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DownloadSubstitutersTest extends BrooklynAppUnitTestSupport {

    private Location loc;
    private TestEntity entity;
    private MyEntityDriver driver;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new SimulatedLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        driver = new MyEntityDriver(entity, loc);
    }
    
    @Test
    public void testSimpleSubstitution() throws Exception {
        entity.setConfig(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        String pattern = "mykey1=${mykey1},mykey2=${mykey2}";
        String result = DownloadSubstituters.substitute(pattern, ImmutableMap.of("mykey1", "myval1", "mykey2", "myval2"));
        assertEquals(result, "mykey1=myval1,mykey2=myval2");
    }

    @Test
    public void testSubstitutionIncludesDefaultSubs() throws Exception {
        entity.setConfig(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        String pattern = "version=${version},type=${type},simpletype=${simpletype}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName()));
    }

    @Test
    public void testSubstitutionDoesMultipleMatches() throws Exception {
        String simpleType = TestEntity.class.getSimpleName();
        String pattern = "simpletype=${simpletype},simpletype=${simpletype}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("simpletype=%s,simpletype=%s", simpleType, simpleType));
    }

    @Test
    public void testSubstitutionUsesEntityBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${entity.id}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSubstitutionUsesDriverBean() throws Exception {
        String entityid = entity.getId();
        String pattern = "id=${driver.entity.id}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, String.format("id=%s", entityid));
    }

    @Test
    public void testSubstitutionUsesOverrides() throws Exception {
        entity.setConfig(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        String pattern = "version=${version},mykey1=${mykey1}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver, ImmutableMap.of("version", "overriddenversion", "mykey1", "myval1"));
        String result = DownloadSubstituters.substitute(req, pattern);
        assertEquals(result, "version=overriddenversion,mykey1=myval1");
    }

    @Test
    public void testThrowsIfUnmatchedSubstitutions() throws Exception {
        String pattern = "nothere=${nothere}";
        BasicDownloadRequirement req = new BasicDownloadRequirement(driver);
        try {
            String result = DownloadSubstituters.substitute(req, pattern);
            fail("Should have failed, but got "+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("${nothere}")) throw e;
        }
    }

    @Test
    public void testSubstituter() throws Exception {
        entity.setConfig(BrooklynConfigKeys.SUGGESTED_VERSION, "myversion");
        String baseurl = "version=${version},type=${type},simpletype=${simpletype}";
        Map<String,Object> subs = DownloadSubstituters.getBasicEntitySubstitutions(driver);
        DownloadTargets result = DownloadSubstituters.substituter(Functions.constant(baseurl), Functions.constant(subs)).apply(new BasicDownloadRequirement(driver));
        String expected = String.format("version=%s,type=%s,simpletype=%s", "myversion", TestEntity.class.getName(), TestEntity.class.getSimpleName());
        assertEquals(result.getPrimaryLocations(), ImmutableList.of(expected));
    }
}
