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

import org.apache.brooklyn.api.entity.basic.EntityLocal;

import brooklyn.location.basic.SshMachineLocation;

/**
 * Implements methods in {@link brooklyn.entity.basic.AbstractSoftwareProcessSshDriver}
 * such that no actions are performed.
 * <p>
 * {@link #isRunning()} returns true.
 */
public class DoNothingSoftwareProcessDriver extends AbstractSoftwareProcessSshDriver {

    public DoNothingSoftwareProcessDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void copyPreInstallResources() {
    }

    @Override
    public void copyInstallResources() {
    }

    @Override
    public void copyRuntimeResources() {
    }

    @Override
    public void install() {
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
    }

    @Override
    public void stop() {
    }
}
