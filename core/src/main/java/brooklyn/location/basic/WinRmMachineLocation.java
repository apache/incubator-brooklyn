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
package brooklyn.location.basic;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.util.flags.SetFromFlag;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

public class WinRmMachineLocation extends AbstractLocation implements MachineLocation {

    public static final ConfigKey<String> WINDOWS_USERNAME = ConfigKeys.newStringConfigKey("windows.username",
            "Username to use when connecting to the remote machine");

    public static final ConfigKey<String> WINDOWS_PASSWORD = ConfigKeys.newStringConfigKey("windows.password",
            "Password to use when connecting to the remote machine");

    @SetFromFlag
    protected String user;

    @SetFromFlag(nullable = false)
    protected InetAddress address;

    @Override
    public InetAddress getAddress() {
        return address;
    }

    @Override
    public OsDetails getOsDetails() {
        return null;
    }

    @Override
    public MachineDetails getMachineDetails() {
        return null;
    }

    @Nullable
    @Override
    public String getHostname() {
        return address.getHostAddress();
    }

    @Override
    public Set<String> getPublicAddresses() {
        return null;
    }

    @Override
    public Set<String> getPrivateAddresses() {
        return null;
    }

    public int executeScript(List<String> script) {
        WinRmTool winRmTool = WinRmTool.connect(getHostname(), getUsername(), getPassword());
        WinRmToolResponse response = winRmTool.executeScript(script);
        return response.getStatusCode();
    }

    public int executePsScript(List<String> psScript) {
        WinRmTool winRmTool = WinRmTool.connect(getHostname(), getUsername(), getPassword());
        WinRmToolResponse response = winRmTool.executePs(psScript);
        return response.getStatusCode();
    }

    public String getUsername() {
        return config().get(WINDOWS_USERNAME);
    }

    private String getPassword() {
        return config().get(WINDOWS_PASSWORD);
    }

}
