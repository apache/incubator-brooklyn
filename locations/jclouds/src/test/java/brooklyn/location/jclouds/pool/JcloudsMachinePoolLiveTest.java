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
package brooklyn.location.jclouds.pool;

import java.util.Arrays;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.jclouds.AbstractJcloudsLiveTest;
import brooklyn.location.jclouds.JcloudsLocation;

public class JcloudsMachinePoolLiveTest extends AbstractJcloudsLiveTest {

    public static final Logger log = LoggerFactory.getLogger(JcloudsMachinePoolLiveTest.class);
    
    private static final String PROVIDER = AWS_EC2_PROVIDER;
    private static final String LOCATION_SPEC = PROVIDER + ":" + AWS_EC2_EUWEST_REGION_NAME;
    
    public static class SamplePool extends MachinePool {
        public SamplePool(ComputeService svc) {
            super(svc);
        }

        public final static ReusableMachineTemplate 
            USUAL_VM = 
                new ReusableMachineTemplate("usual").templateOwnedByMe().
                tagOptional("tagForUsualVm").
                metadataOptional("metadataForUsualVm", "12345").
                minRam(1024).minCores(2);

        public final static ReusableMachineTemplate 
            ANYONE_NOT_TINY_VM = 
                new ReusableMachineTemplate("anyone").
                minRam(512).minCores(1).strict(false);

        public static final ReusableMachineTemplate 
            VM_LARGE1 = 
                new ReusableMachineTemplate("vm.large1").templateOwnedByMe().
                minRam(16384).minCores(4),
            VM_SMALL1 = 
                new ReusableMachineTemplate("vm.small1").templateOwnedByMe().smallest();
        
        { registerTemplates(USUAL_VM, ANYONE_NOT_TINY_VM, VM_LARGE1, VM_SMALL1); }
    }
    
    private ComputeServiceContext context;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(LOCATION_SPEC);
        
        context = ContextBuilder.newBuilder(PROVIDER)
                .modules(Arrays.asList(new SshjSshClientModule(), new SLF4JLoggingModule()))
                .credentials(jcloudsLocation.getIdentity(), jcloudsLocation.getCredential())
                .build(ComputeServiceContext.class);
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if (context != null) context.close();
        }
    }
    
    @Test(groups={"Live","WIP"})
    public void buildClaimAndDestroy() {
        ComputeService svc = context.getComputeService();
        SamplePool p = new SamplePool(svc);
        log.info("buildClaimAndDestroy: created pool");
        p.refresh();
        log.info("buildClaimAndDestroy: refreshed pool");
        p.ensureExists(2, SamplePool.USUAL_VM);
        log.info("buildClaimAndDestroy: ensure have 2");
        MachineSet l = p.claim(1, SamplePool.USUAL_VM);
        Assert.assertEquals(l.size(), 1);
        log.info("buildClaimAndDestroy: claimed 1");
        MachineSet unclaimedUsual = p.unclaimed(MachinePoolPredicates.matching(SamplePool.USUAL_VM));
        log.info("buildClaimAndDestroy: unclaimed now "+unclaimedUsual);
        Assert.assertTrue(!unclaimedUsual.isEmpty());
        p.destroy(unclaimedUsual);
        unclaimedUsual = p.unclaimed(MachinePoolPredicates.matching(SamplePool.USUAL_VM));
        log.info("buildClaimAndDestroy: destroyed, unclaimed now "+unclaimedUsual);
        log.info("end");
    }
    

    
}
