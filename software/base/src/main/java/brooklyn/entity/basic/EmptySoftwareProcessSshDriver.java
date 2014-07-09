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

import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.location.basic.SshMachineLocation;


public class EmptySoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements EmptySoftwareProcessDriver {

    private final AtomicBoolean running = new AtomicBoolean();

    public EmptySoftwareProcessSshDriver(EmptySoftwareProcessImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void install() {
    }

    @Override
    public void customize() {
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
