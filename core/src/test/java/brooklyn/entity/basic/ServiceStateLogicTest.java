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
package brooklyn.entity.basic;

import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceProblemsLogic;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl.TestEntityWithoutEnrichers;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Test
public class ServiceStateLogicTest extends BrooklynAppUnitTestSupport {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceStateLogicTest.class);
    
    final static String INDICATOR_KEY_1 = "test-indicator-1";
    final static String INDICATOR_KEY_2 = "test-indicator-2";

    protected TestEntity entity;

    @Override
    protected void setUpApp() {
        super.setUpApp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }


    public void testManuallySettingIndicatorsOnEntities() {
        // if we set a not up indicator, entity service up should become false
        ServiceNotUpLogic.updateNotUpIndicator(entity, INDICATOR_KEY_1, "We're pretending to block service up");
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
        
        // but state will not change unless we also set either a problem or expected state
        assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, null);
        ServiceProblemsLogic.updateProblemsIndicator(entity, INDICATOR_KEY_1, "We're pretending to block service state also");
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        
        // and if we clear the not up indicator, service up becomes true, but there is a problem, so it shows on-fire
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_1);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // if we then clear the problem also, state goes to RUNNING
        ServiceProblemsLogic.clearProblemsIndicator(entity, INDICATOR_KEY_1);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        // now add not-up indicator again, and it reverts to up=false, state=stopped
        ServiceNotUpLogic.updateNotUpIndicator(entity, INDICATOR_KEY_1, "We're again pretending to block service up");
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        
        // but if we expect it to be running it will show on fire (because service is not up)
        ServiceStateLogic.setExpectedState(entity, Lifecycle.RUNNING);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // and if we again clear the not up indicator it will deduce up=true and state=running
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_1);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

    public void testAppStoppedAndEntityNullBeforeStarting() {
        // AbstractApplication has default logic to ensure service is not up if it hasn't been started,
        // (this can be removed by updating the problem indicator associated with the SERVICE_STATE_ACTUAL sensor)
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, false);
        // and from that it imputes stopped state
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        
        // TestEntity has no such indicators however
        assertAttributeEquals(entity, Attributes.SERVICE_UP, null);
        assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, null);
    }
    
    public void testAllUpAndRunningAfterStart() {
        app.start(ImmutableList.<Location>of());
        
        assertAttributeEquals(app, Attributes.SERVICE_UP, true);
        assertAttributeEquals(entity, Attributes.SERVICE_UP, true);
        // above should be immediate, entity should then derive RUNNING from expected state, and then so should app from children
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
    
    public void testStopsNicelyToo() {
        app.start(ImmutableList.<Location>of());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        app.stop();
        
        assertAttributeEquals(app, Attributes.SERVICE_UP, false);
        assertAttributeEquals(entity, Attributes.SERVICE_UP, false);
        // above should be immediate, app and entity should then derive STOPPED from the expected state
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
    }

    public void testTwoIndicatorsAreBetterThanOne() {        
        // if we set a not up indicator, entity service up should become false
        ServiceNotUpLogic.updateNotUpIndicator(entity, INDICATOR_KEY_1, "We're pretending to block service up");
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
        ServiceNotUpLogic.updateNotUpIndicator(entity, INDICATOR_KEY_2, "We're also pretending to block service up");
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_1);
        // clearing one indicator is not sufficient
        assertAttributeEquals(entity, Attributes.SERVICE_UP, false);
        
        // but it does not become true when both are cleared
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_2);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
    }

    @Test(invocationCount=100, groups="Integration")
    public void testManuallySettingIndicatorsOnApplicationsManyTimes() throws Exception {
        testManuallySettingIndicatorsOnApplications();
    }

    public void testManuallySettingIndicatorsOnApplications() throws Exception {
        // indicators on application are more complicated because it is configured with additional indicators from its children
        // test a lot of situations, including reconfiguring some of the quorum config
        
        // to begin with, an entity has not reported anything, so the ComputeServiceIndicatorsFromChildren ignores it
        // but the AbstractApplication has emitted a not-up indicator because it has not been started
        // both as asserted by this other test:
        testAppStoppedAndEntityNullBeforeStarting();
        
        // if we clear the not up indicator, the app will show as up, and as running, because it has no reporting children 
        ServiceNotUpLogic.clearNotUpIndicator(app, Attributes.SERVICE_STATE_ACTUAL);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        // if we then put a not-up indicator on the TestEntity, it publishes false, but app is still up. State
        // won't propagate due to it's SERVICE_STATE_ACTUAL (null) being in IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES
        ServiceNotUpLogic.updateNotUpIndicator(entity, INDICATOR_KEY_1, "We're also pretending to block service up");
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
        assertAttributeEqualsContinually(app, Attributes.SERVICE_UP, true);
        assertAttributeEqualsContinually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        // the entity still has no opinion about its state
        assertAttributeEqualsContinually(entity, Attributes.SERVICE_STATE_ACTUAL, null);
        
        // switching the entity state to one not in IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES will propagate the up state
        ServiceStateLogic.setExpectedState(entity, Lifecycle.RUNNING);
        assertAttributeEqualsContinually(entity, Attributes.SERVICE_UP, false);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, false);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);

        
        // if the entity expects to be stopped, it will report stopped
        ServiceStateLogic.setExpectedState(entity, Lifecycle.STOPPED);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        // and the app will ignore the entity state, so becomes running
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        
        // if we clear the not-up indicator, both the entity and the app report service up (with the entity first)
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_1);
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        // but entity is still stopped because that is what is expected there, and that's okay even if service is apparently up
        assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        // the app however is running, because the default state quorum check is "all are healthy"
        assertAttributeEqualsContinually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEqualsContinually(app, Attributes.SERVICE_UP, true);
        
        // if we change the state quorum check for the app to be "all are healthy and at least one running" *then* it shows stopped
        // (normally this would be done in `initEnrichers` of course)
        Enricher appChildrenBasedEnricher = EntityAdjuncts.tryFindWithUniqueTag(app.getEnrichers(), ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG).get();
        appChildrenBasedEnricher.config().set(ComputeServiceIndicatorsFromChildrenAndMembers.RUNNING_QUORUM_CHECK, QuorumChecks.allAndAtLeastOne());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // if entity is expected running, then it will show running because service is up; this is reflected at app and at entity
        ServiceStateLogic.setExpectedState(entity, Lifecycle.RUNNING);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        // now, when the entity is unmanaged, the app goes on fire because don't have "at least one running"
        Entities.unmanage(entity);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        // but UP_QUORUM_CHECK is still the default atLeastOneUnlessEmpty; so serviceUp=true
        assertAttributeEqualsContinually(app, Attributes.SERVICE_UP, true);
        
        // if we change its up-quorum to atLeastOne then state becomes stopped (because there is no expected state; has not been started)
        appChildrenBasedEnricher.config().set(ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK, QuorumChecks.atLeastOne());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, false);
        
        // if we now start it will successfully start (because unlike entities it does not wait for service up) 
        // but will remain down and will go on fire
        app.start(ImmutableList.<Location>of());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, false);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // restoring up-quorum to "atLeastOneUnlessEmpty" causes it to become RUNNING, because happy with empty
        appChildrenBasedEnricher.config().set(ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        // but running-quorum is still allAndAtLeastOne, so remains on-fire
        assertAttributeEqualsContinually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // now add a child, it's still up and running because null values are ignored by default (i.e. we're still "empty")
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertAttributeEqualsContinually(app, Attributes.SERVICE_UP, true);
        assertAttributeEqualsContinually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // tell it not to ignore null values for children states, and it will go onfire (but still be service up)
        appChildrenBasedEnricher.config().set(ComputeServiceIndicatorsFromChildrenAndMembers.IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES, 
            ImmutableSet.<Lifecycle>of());
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertAttributeEquals(app, Attributes.SERVICE_UP, true);
        
        // tell it not to ignore null values for service up and it will go service down
        appChildrenBasedEnricher.config().set(ComputeServiceIndicatorsFromChildrenAndMembers.IGNORE_ENTITIES_WITH_SERVICE_UP_NULL, false);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, false);
        
        // set the entity to RUNNING and the app will be healthy again
        ServiceNotUpLogic.clearNotUpIndicator(entity, INDICATOR_KEY_1);
        ServiceStateLogic.setExpectedState(entity, Lifecycle.RUNNING);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_UP, true);
        assertAttributeEqualsEventually(app, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertAttributeEquals(entity, Attributes.SERVICE_UP, true);
        assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

    @Test
    public void testQuorumWithStringStates() {
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntityWithoutEnrichers.class))
                .configure(DynamicCluster.INITIAL_SIZE, 1));

        cluster.start(ImmutableList.of(app.newSimulatedLocation()));
        EntityTestUtils.assertGroupSizeEqualsEventually(cluster, 1);

        //manually set state to healthy as enrichers are disabled
        EntityInternal child = (EntityInternal) cluster.getMembers().iterator().next();
        child.setAttribute(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        child.setAttribute(Attributes.SERVICE_UP, Boolean.TRUE);

        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        //set untyped service state, the quorum check should be able to handle coercion
        AttributeSensor<Object> stateSensor = Sensors.newSensor(Object.class, Attributes.SERVICE_STATE_ACTUAL.getName());
        child.setAttribute(stateSensor, "running");

        EntityTestUtils.assertAttributeEqualsContinually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

    private static <T> void assertAttributeEqualsEventually(Entity x, AttributeSensor<T> sensor, T value) {
        try {
            EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", Duration.seconds(3)), x, sensor, value);
        } catch (Throwable e) {
            log.warn("Expected "+x+" eventually to have "+sensor+" = "+value+"; instead:");
            Entities.dumpInfo(x);
            throw Exceptions.propagate(e);
        }
    }
    private static <T> void assertAttributeEqualsContinually(Entity x, AttributeSensor<T> sensor, T value) {
        try {
            EntityTestUtils.assertAttributeEqualsContinually(ImmutableMap.of("timeout", Duration.millis(25)), x, sensor, value);
        } catch (Throwable e) {
            log.warn("Expected "+x+" continually to have "+sensor+" = "+value+"; instead:");
            Entities.dumpInfo(x);
            throw Exceptions.propagate(e);
        }
    }
    private static <T> void assertAttributeEquals(Entity x, AttributeSensor<T> sensor, T value) {
        try {
            EntityTestUtils.assertAttributeEquals(x, sensor, value);
        } catch (Throwable e) {
            log.warn("Expected "+x+" to have "+sensor+" = "+value+"; instead:");
            Entities.dumpInfo(x);
            throw Exceptions.propagate(e);
        }
    }

}
