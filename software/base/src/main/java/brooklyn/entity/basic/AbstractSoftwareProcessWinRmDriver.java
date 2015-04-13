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

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.WinRmMachineLocation;

import com.google.api.client.util.Strings;
import com.google.common.collect.ImmutableList;

public abstract class AbstractSoftwareProcessWinRmDriver extends AbstractSoftwareProcessDriver {

    public AbstractSoftwareProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void runPreInstallCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void setup() {
        // Default to no-op
    }

    @Override
    public void runPostInstallCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void runPreLaunchCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void runPostLaunchCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public WinRmMachineLocation getLocation() {
        return (WinRmMachineLocation)super.getLocation();
    }

    @Override
    public String getRunDir() {
        // TODO: This needs to be tidied, and read from the appropriate flags (if set)
        return "$HOME\\brooklyn-managed-processes\\apps\\" + entity.getApplicationId() + "\\entities\\" + getEntityVersionLabel()+"_"+entity.getId();
    }

    @Override
    public String getInstallDir() {
        // TODO: This needs to be tidied, and read from the appropriate flags (if set)
        return "$HOME\\brooklyn-managed-processes\\installs\\" + entity.getApplicationId() + "\\" + getEntityVersionLabel()+"_"+entity.getId();
    }

    @Override
    public int copyResource(Map<Object, Object> sshFlags, String source, String target, boolean createParentDir) {
        if (createParentDir) {
            createDirectory(getDirectory(target), "Creating resource directory");
        }
        return copyTo(new File(source), new File(target));
    }

    @Override
    public int copyResource(Map<Object, Object> sshFlags, InputStream source, String target, boolean createParentDir) {
        if (createParentDir) {
            createDirectory(getDirectory(target), "Creating resource directory");
        }
        return copyTo(source, new File(target));
    }

    @Override
    protected void createDirectory(String directoryName, String summaryForLogging) {
        getLocation().executePsScript("New-Item -path \"" + directoryName + "\" -type directory -ErrorAction SilentlyContinue");
    }

    protected boolean executeCommand(ConfigKey<String> regularCommandKey, ConfigKey<String> powershellCommandKey) {
        String regularCommand = getEntity().getConfig(regularCommandKey);
        String powershellCommand = getEntity().getConfig(powershellCommandKey);
        if ((Strings.isNullOrEmpty(regularCommand) && Strings.isNullOrEmpty(powershellCommand)) || (
                !Strings.isNullOrEmpty(regularCommand) && !Strings.isNullOrEmpty(powershellCommand))) {
            throw new IllegalStateException(String.format("Exactly one of %s or %s must be set", regularCommandKey.getName(), powershellCommandKey.getName()));
        }
        if (Strings.isNullOrEmpty(regularCommand)) {
            return getLocation().executePsScript(ImmutableList.of(powershellCommand)) == 0;
        } else {
            return getLocation().executeScript(ImmutableList.of(regularCommand)) == 0;
        }
    }

    public int execute(List<String> script) {
        return getLocation().executeScript(script);
    }

    public int executePowerShell(List<String> psScript) {
        return getLocation().executePsScript(psScript);
    }

    public int copyTo(File source, File destination) {
        return getLocation().copyTo(source, destination);
    }

    public int copyTo(InputStream source, File destination) {
        return getLocation().copyTo(source, destination);
    }

    private String getDirectory(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf("\\"));
    }

}
