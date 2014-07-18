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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;

/**
 * Mock application for testing.
 */
//TODO Don't want to extend EntityLocal/EntityInternal, but tests want to call things like app.setAttribute
@ImplementedBy(TestApplicationImpl.class)
public interface TestApplication extends StartableApplication, EntityInternal {

    public static final AttributeSensor<String> MY_ATTRIBUTE = Sensors.newStringSensor("test.myattribute", "Test attribute sensor");

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);

    public LocalhostMachineProvisioningLocation newLocalhostProvisioningLocation();

    public static class Factory {
        public static TestApplication newManagedInstanceForTests() {
            return ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContextForTests());
        }
    }
}
