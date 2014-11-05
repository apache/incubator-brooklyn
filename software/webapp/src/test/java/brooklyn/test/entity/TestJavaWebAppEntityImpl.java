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
package brooklyn.test.entity;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.SoftwareProcessDriverLifecycleEffectorTasks;
import brooklyn.entity.java.VanillaJavaAppImpl;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;

public class TestJavaWebAppEntityImpl extends VanillaJavaAppImpl implements TestJavaWebAppEntity {

    private static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntity.class);

    @SetFromFlag public int a;
    @SetFromFlag public int b;
    @SetFromFlag public int c;

    public TestJavaWebAppEntityImpl() {}
    
    // constructor required for use in DynamicCluster.factory
    public TestJavaWebAppEntityImpl(@SuppressWarnings("rawtypes") Map flags, Entity parent) { super(flags, parent); }

    private static final SoftwareProcessDriverLifecycleEffectorTasks LIFECYCLE_TASKS =
        new SoftwareProcessDriverLifecycleEffectorTasks() {
        public void start(java.util.Collection<? extends Location> locations) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
            LOG.trace("Starting {}", this);
            entity().setAttribute(SERVICE_PROCESS_IS_RUNNING, true);
            entity().setAttribute(Attributes.SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
        }
        public void stop() {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
            LOG.trace("Stopping {}", this);
            entity().setAttribute(Attributes.SERVICE_UP, false);
            entity().setAttribute(SERVICE_PROCESS_IS_RUNNING, false);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPED);
        }
    };

    @Override
    public void init() {
        super.init();
        LIFECYCLE_TASKS.attachLifecycleEffectors(this);
    }

    @Override
    public synchronized void spoofRequest() {
        Integer rc = getAttribute(WebAppServiceConstants.REQUEST_COUNT);
        if (rc==null) rc = 0;
        setAttribute(WebAppServiceConstants.REQUEST_COUNT, rc+1);
    }

    @Override
    public int getA() {
        return a;
    }
    
    @Override
    public int getB() {
        return b;
    }
    
    @Override
    public int getC() {
        return c;
    }
}
