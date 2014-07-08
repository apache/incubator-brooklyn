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
package brooklyn.util.task.ssh;

import java.io.InputStream;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Supplier;

public class SshPutTaskStub {

    protected String remoteFile;
    protected SshMachineLocation machine;
    protected Supplier<? extends InputStream> contents;
    protected String summary;
    protected String permissions;
    protected boolean allowFailure = false;
    protected boolean createDirectory = false;
    protected final ConfigBag config = ConfigBag.newInstance();

    protected SshPutTaskStub() {
    }
    
    protected SshPutTaskStub(SshPutTaskStub constructor) {
        this.remoteFile = constructor.remoteFile;
        this.machine = constructor.machine;
        this.contents = constructor.contents;
        this.summary = constructor.summary;
        this.allowFailure = constructor.allowFailure;
        this.createDirectory = constructor.createDirectory;
        this.permissions = constructor.permissions;
        this.config.copy(constructor.config);
    }

    public String getRemoteFile() {
        return remoteFile;
    }
    
    public String getSummary() {
        if (summary!=null) return summary;
        return "scp put: "+remoteFile;
    }

    public SshMachineLocation getMachine() {
        return machine;
    }
    
    protected ConfigBag getConfig() {
        return config;
    }
}
