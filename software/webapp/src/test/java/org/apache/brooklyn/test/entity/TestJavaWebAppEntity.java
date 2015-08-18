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
package org.apache.brooklyn.test.entity;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessDriverLifecycleEffectorTasks;
import brooklyn.entity.java.VanillaJavaApp;

/**
 * Mock web application server entity for testing.
 */
@ImplementedBy(TestJavaWebAppEntityImpl.class)
public interface TestJavaWebAppEntity extends VanillaJavaApp, WebAppService, EntityLocal {

    /**
     * Injects the test entity's customised lifecycle tasks.
     */
    ConfigKey<SoftwareProcessDriverLifecycleEffectorTasks> LIFECYCLE_EFFECTOR_TASKS = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.LIFECYCLE_EFFECTOR_TASKS,
            new TestJavaWebAppEntityLifecycleTasks());

    void spoofRequest();
    int getA();
    int getB();
    int getC();

    static class TestJavaWebAppEntityLifecycleTasks extends SoftwareProcessDriverLifecycleEffectorTasks {
        private static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntityLifecycleTasks.class);

        @Override
        public void start(java.util.Collection<? extends Location> locations) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
            LOG.trace("Starting {}", this);
            entity().setAttribute(SERVICE_PROCESS_IS_RUNNING, true);
            entity().setAttribute(Attributes.SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
        }

        @Override
        public void stop(ConfigBag parameters) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
            LOG.trace("Stopping {}", this);
            entity().setAttribute(Attributes.SERVICE_UP, false);
            entity().setAttribute(SERVICE_PROCESS_IS_RUNNING, false);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPED);
        }
    }

}
