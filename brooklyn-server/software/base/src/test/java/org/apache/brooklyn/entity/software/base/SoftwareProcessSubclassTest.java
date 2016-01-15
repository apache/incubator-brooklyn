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
package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.effector.EffectorAndBody;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcessImpl;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;


public class SoftwareProcessSubclassTest extends BrooklynAppUnitTestSupport {

//  NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessSubclassTest.class);

    @ImplementedBy(SubSoftwareProcessImpl.class)
    public static interface SubSoftwareProcess extends EmptySoftwareProcess {
        public List<String> getCallHistory();
        public void triggerStopOutsideOfEffector();
        public void customRestart();
    }
    
    public static class SubSoftwareProcessImpl extends EmptySoftwareProcessImpl implements SubSoftwareProcess {
        
        protected List<String> callHistory = Collections.synchronizedList(Lists.<String>newArrayList());

        @Override
        public void init() {
            super.init();
            getMutableEntityType().addEffector(new EffectorAndBody<Void>(SoftwareProcess.RESTART, new MethodEffector<Void>(SubSoftwareProcess.class, "customRestart").getBody()));
        }
        
        @Override
        public List<String> getCallHistory() {
            return callHistory;
        }

        @Override
        public void preStart() {
            callHistory.add("doStart");
            super.preStart();
        }
        
        @Override
        public void preStop() {
            callHistory.add("doStop");
            super.preStop();
        }
        
        @Override
        public void customRestart() {
            callHistory.add("doRestart");
        }
        
        @Override
        public void triggerStopOutsideOfEffector() {
            stop();
        }
        
    }
    
    private Location loc;
    private List<Location> locs;
    private SubSoftwareProcess entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationRegistry().resolve("localhost");
        locs = ImmutableList.of(loc);
        entity = app.createAndManageChild(EntitySpec.create(SubSoftwareProcess.class));
    }

    @Test
    public void testStartCalledViaMethod() throws Exception {
        entity.start(locs);
        
        assertCallHistory(ImmutableList.of("doStart"));
    }

    @Test
    public void testStopCalledViaMethod() throws Exception {
        app.start(locs);
        entity.stop();
        
        assertCallHistory(ImmutableList.of("doStart", "doStop"));
    }

    @Test
    public void testRestartCalledViaMethod() throws Exception {
        app.start(locs);
        entity.restart();
        
        assertCallHistory(ImmutableList.of("doStart", "doRestart"));
    }
    
    @Test
    public void testStopCalledWithoutEffector() throws Exception {
        app.start(locs);
        entity.triggerStopOutsideOfEffector();
        
        assertCallHistory(ImmutableList.of("doStart", "doStop"));
    }

    @Test
    public void testStartCalledViaInvokeEffector() throws Exception {
        entity.invoke(SubSoftwareProcess.START, ImmutableMap.<String,Object>of("locations", locs)).get();
        
        assertCallHistory(ImmutableList.of("doStart"));
    }

    @Test
    public void testStopCalledViaInvokeEffector() throws Exception {
        app.start(locs);
        entity.invoke(SubSoftwareProcess.STOP, ImmutableMap.<String,Object>of()).get();
        
        assertCallHistory(ImmutableList.of("doStart", "doStop"));
    }

    @Test
    public void testRestartCalledViaInvokeEffector() throws Exception {
        app.start(locs);
        entity.invoke(SubSoftwareProcess.RESTART, ImmutableMap.<String,Object>of()).get();
        
        assertCallHistory(ImmutableList.of("doStart", "doRestart"));
    }
    
    private void assertCallHistory(Iterable<String> expected) {
        List<String> actual = entity.getCallHistory();
        assertEquals(actual, ImmutableList.copyOf(expected), "actual="+actual);
    }
}
