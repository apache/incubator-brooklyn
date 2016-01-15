/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.exceptions.Exceptions;

/**
 * Created by graememiller on 11/12/2015.
 */
public class LoopOverGroupMembersTestCaseImpl extends TargetableTestComponentImpl implements LoopOverGroupMembersTestCase {

    private static final Logger logger = LoggerFactory.getLogger(LoopOverGroupMembersTestCaseImpl.class);

    @Override
    public void start(@EffectorParam(name = "locations") Collection<? extends Location> locations) {
        // Let everyone know we're starting up (so that the GUI shows the correct icon).
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);

        Entity target = resolveTarget();
        if (target == null) {
            logger.debug("Tasks NOT successfully run. LoopOverGroupMembersTestCaseImpl group not set");
            setServiceState(false, Lifecycle.ON_FIRE);
            return;
        }

        if (!(target instanceof Group)) {
            logger.debug("Tasks NOT successfully run. LoopOverGroupMembersTestCaseImpl target is not a group");
            setServiceState(false, Lifecycle.ON_FIRE);
            return;
        }

        EntitySpec<? extends TargetableTestComponent> testSpec = config().get(TEST_SPEC);
        if (testSpec == null) {
            logger.debug("Tasks NOT successfully run. LoopOverGroupMembersTestCaseImpl test spec not set");
            setServiceState(false, Lifecycle.ON_FIRE);
            return;
        }

        Group group = (Group) target;

        Collection<Entity> children = group.getMembers();
        boolean allSuccesful = true;
        for (Entity child : children) {
            testSpec.configure(TestCase.TARGET_ENTITY, child);

            try {
                TargetableTestComponent targetableTestComponent = this.addChild(testSpec);
                targetableTestComponent.start(locations);
            } catch (Throwable t) {
                allSuccesful = false;
            }
        }

        if (allSuccesful) {
            // Let everyone know we've started up successfully (changes the icon in the GUI).
            logger.debug("Tasks successfully run. Update state of {} to RUNNING.", this);
            setServiceState(true, Lifecycle.RUNNING);
        } else {
            // Let everyone know we've npt started up successfully (changes the icon in the GUI).
            logger.debug("Tasks NOT successfully run. Update state of {} to ON_FIRE.", this);
            setServiceState(false, Lifecycle.ON_FIRE);
        }

    }

    @Override
    public void stop() {
        // Let everyone know we're stopping (so that the GUI shows the correct icon).
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPING);

        try {
            for (Entity child : this.getChildren()) {
                if (child instanceof Startable) ((Startable) child).stop();
            }

            // Let everyone know we've stopped successfully (changes the icon in the GUI).
            logger.debug("Tasks successfully run. Update state of {} to STOPPED.", this);
            setServiceState(false, Lifecycle.STOPPED);
        } catch (Throwable t) {
            logger.debug("Tasks NOT successfully run. Update state of {} to ON_FIRE.", this);
            setServiceState(false, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    @Override
    public void restart() {
        final Collection<Location> locations = Lists.newArrayList(getLocations());
        stop();
        start(locations);
    }

    /**
     * Sets the state of the Entity. Useful so that the GUI shows the correct icon.
     *
     * @param serviceUpState     Whether or not the entity is up.
     * @param serviceStateActual The actual state of the entity.
     */
    private void setServiceState(final boolean serviceUpState, final Lifecycle serviceStateActual) {
        sensors().set(SERVICE_UP, serviceUpState);
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, serviceStateActual);
    }
}
