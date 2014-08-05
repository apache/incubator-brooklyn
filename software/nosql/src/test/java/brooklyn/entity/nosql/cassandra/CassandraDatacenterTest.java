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
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.EmptySoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class CassandraDatacenterTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CassandraDatacenterTest.class);
    
    private LocalhostMachineProvisioningLocation loc;
    private CassandraDatacenter cluster;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }
    
    @Test
    public void testPopulatesInitialSeeds() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                .configure(CassandraDatacenter.TOKEN_SHIFT, BigInteger.ZERO)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));
        EmptySoftwareProcess e1 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 0);
        EmptySoftwareProcess e2 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 1);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CURRENT_SEEDS, ImmutableSet.<Entity>of(e1, e2));
    }
    
    @Test(groups="Integration") // because takes approx 2 seconds
    public void testUpdatesSeedsOnFailuresAndAdditions() throws Exception {
        doTestUpdatesSeedsOnFailuresAndAdditions(true, false);
    }
    
    protected void doTestUpdatesSeedsOnFailuresAndAdditions(boolean fast, boolean checkSeedsConstantOnRejoining) throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                .configure(CassandraDatacenter.TOKEN_SHIFT, BigInteger.ZERO)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));
        EmptySoftwareProcess e1 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 0);
        EmptySoftwareProcess e2 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 1);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CURRENT_SEEDS, ImmutableSet.<Entity>of(e1, e2));
        log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; e1="+e1+" e2="+e2);
        
        // calling the driver stop for this entity will cause SERVICE_UP to become false, and stay false
        // (and that's all it does, incidentally); if we just set the attribute it will become true on serviceUp sensor feed
        ((EmptySoftwareProcess)e1).getDriver().stop();
        // not necessary, but speeds things up:
        if (fast)
            ((EntityInternal)e1).setAttribute(Attributes.SERVICE_UP, false);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2));

        cluster.resize(3);
        EmptySoftwareProcess e3 = (EmptySoftwareProcess) Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(e1,e2)));
        log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; e3="+e3);
        try {
            EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2, e3));
        } finally {
            log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; seeds "+cluster.getAttribute(CassandraDatacenter.CURRENT_SEEDS));
        }
        
        if (!checkSeedsConstantOnRejoining) {
            // cluster should not revert to e1+e2, simply because e1 has come back; but e1 should rejoin the group
            // (not that important, and waits for 1s, so only done as part of integration)
            ((EmptySoftwareProcessSshDriver)(((EmptySoftwareProcess)e1).getDriver())).launch();
            if (fast)
                ((EntityInternal)e1).setAttribute(Attributes.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(e1, CassandraNode.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsContinually(cluster, CassandraDatacenter.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2, e3));
        }
    }
    
    @Test
    public void testPopulatesInitialTokens() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                .configure(CassandraDatacenter.TOKEN_SHIFT, BigInteger.ZERO)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));

        Set<BigInteger> tokens = Sets.newLinkedHashSet();
        Set<BigInteger> tokens2 = Sets.newLinkedHashSet();
        for (Entity member : cluster.getMembers()) {
            BigInteger memberToken = member.getConfig(CassandraNode.TOKEN);
            Set<BigInteger > memberTokens = member.getConfig(CassandraNode.TOKENS);
            if (memberToken != null) tokens.add(memberToken);
            if (memberTokens != null) tokens2.addAll(memberTokens);
        }
        assertEquals(tokens, ImmutableSet.of(new BigInteger("-9223372036854775808"), BigInteger.ZERO));
        assertEquals(tokens2, ImmutableSet.of());
    }
    
    @Test
    public void testDoesNotPopulateInitialTokens() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                .configure(CassandraDatacenter.USE_VNODES, true)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));

        Set<BigInteger> tokens = Sets.newLinkedHashSet();
        Set<BigInteger> tokens2 = Sets.newLinkedHashSet();
        for (Entity member : cluster.getMembers()) {
            BigInteger memberToken = member.getConfig(CassandraNode.TOKEN);
            Set<BigInteger > memberTokens = member.getConfig(CassandraNode.TOKENS);
            if (memberToken != null) tokens.add(memberToken);
            if (memberTokens != null) tokens2.addAll(memberTokens);
        }
        assertEquals(tokens, ImmutableSet.of());
        assertEquals(tokens2, ImmutableSet.of());
    }
    
    public static class MockInputForTemplate {
        public BigInteger getToken() { return new BigInteger("-9223372036854775808"); }
        public String getTokensAsString() { return "" + getToken(); }
        public int getNumTokensPerNode() { return 1; }
        public String getSeeds() { return ""; }
        public int getGossipPort() { return 1234; }
        public int getSslGossipPort() { return 1234; }
        public int getThriftPort() { return 1234; }
        public int getNativeTransportPort() { return 1234; }
        public String getClusterName() { return "Mock"; }
        public String getEndpointSnitchName() { return ""; }
        public String getListenAddress() { return "0"; }
        public String getBroadcastAddress() { return "0"; }
        public String getRpcAddress() { return "0"; }
        public String getRunDir() { return "/tmp/mock"; }
    }
    
    @Test
    public void testBigIntegerFormattedCorrectly() {
        Map<String, Object> substitutions = ImmutableMap.<String, Object>builder()
                .put("entity", new MockInputForTemplate())
                .put("driver", new MockInputForTemplate())
                .build();

        String templatedUrl = CassandraNode.CASSANDRA_CONFIG_TEMPLATE_URL.getDefaultValue();
        String url = TemplateProcessor.processTemplateContents(templatedUrl, ImmutableMap.of("entity", ImmutableMap.of("majorMinorVersion", "1.2")));
        String templateContents = new ResourceUtils(this).getResourceAsString(url);
        String processedTemplate = TemplateProcessor.processTemplateContents(templateContents, substitutions);
        Assert.assertEquals(processedTemplate.indexOf("775,808"), -1);
        Assert.assertTrue(processedTemplate.indexOf("-9223372036854775808") > 0);
    }
    
    @Test(groups="Integration") // because takes approx 30 seconds
    public void testUpdatesSeedsFastishManyTimes() throws Exception {
        final int COUNT = 20;
        for (int i=0; i<COUNT; i++) {
            log.info("Test "+JavaClassNames.niceClassAndMethod()+", iteration "+(i+1)+" of "+COUNT);
            try {
                doTestUpdatesSeedsOnFailuresAndAdditions(true, true);
                tearDown();
                setUp();
            } catch (Exception e) {
                log.warn("Error in "+JavaClassNames.niceClassAndMethod()+", iteration "+(i+1)+" of "+COUNT, e);
                throw e;
            }
        }
    }
    
    @Test(groups="Integration") // because takes approx 5 seconds
    public void testUpdateSeedsSlowAndRejoining() throws Exception {
        final int COUNT = 1;
        for (int i=0; i<COUNT; i++) {
            log.info("Test "+JavaClassNames.niceClassAndMethod()+", iteration "+(i+1)+" of "+COUNT);
            doTestUpdatesSeedsOnFailuresAndAdditions(false, true);
            tearDown();
            setUp();
        }
    }

}
