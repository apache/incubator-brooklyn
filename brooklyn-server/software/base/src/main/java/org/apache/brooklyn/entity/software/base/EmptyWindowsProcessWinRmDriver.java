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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.net.UserAndHostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptyWindowsProcessWinRmDriver extends AbstractSoftwareProcessWinRmDriver implements VanillaWindowsProcessDriver {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(EmptyWindowsProcessWinRmDriver.class);

    private final AtomicBoolean running = new AtomicBoolean();

    public EmptyWindowsProcessWinRmDriver(EmptyWindowsProcessImpl entity, WinRmMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void start() {
        WinRmMachineLocation machine = (WinRmMachineLocation) location;
        UserAndHostAndPort winrmAddress = UserAndHostAndPort.fromParts(machine.getUser(), machine.getAddress().getHostName(), machine.config().get(WinRmMachineLocation.WINRM_PORT));
        getEntity().sensors().set(Attributes.WINRM_ADDRESS, winrmAddress);

        super.start();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void install() { }

    @Override
    public void customize() { }

    @Override
    public void copyInstallResources() { 
        Map<String, String> installFiles = entity.getConfig(SoftwareProcess.INSTALL_FILES);
        Map<String, String> installTemplates = entity.getConfig(SoftwareProcess.INSTALL_TEMPLATES);
        if ((installFiles!=null && !installFiles.isEmpty()) || (installTemplates!=null && !installTemplates.isEmpty())) {
            // only do this if there are files, to prevent unnecessary `mkdir`
            super.copyInstallResources();
        }
    }

    @Override
    public void copyRuntimeResources() { 
        Map<String, String> runtimeFiles = entity.getConfig(SoftwareProcess.RUNTIME_FILES);
        Map<String, String> runtimeTemplates = entity.getConfig(SoftwareProcess.RUNTIME_TEMPLATES);
        if ((runtimeFiles!=null && !runtimeFiles.isEmpty()) || (runtimeTemplates!=null && !runtimeTemplates.isEmpty())) {
            // only do this if there are files, to prevent unnecessary `mkdir`
            super.copyRuntimeResources();
        }        
    }

    @Override
    public void launch() {
        running.set(true);
    }

    @Override
    public void rebind() {
        super.rebind();
        /* TODO not necessarily, but there is not yet an easy way to persist state without 
         * using config/sensors which we might not want do. */
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }
}
