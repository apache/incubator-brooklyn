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

import brooklyn.location.basic.WinRmMachineLocation;

public abstract class AbstractSoftwareProcessWinRmDriver extends AbstractSoftwareProcessDriver {

    public AbstractSoftwareProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
    }

    public WinRmMachineLocation getMachine() {
        return (WinRmMachineLocation) getLocation();
    }

    public int execute(List<String> script) {
        return getMachine().executeScript(script);
    }

    public int executePowerShell(List<String> psScript) {
        return getMachine().executePsScript(psScript);
    }

    public int copyTo(File source, File destination) {
        return getMachine().copyTo(source, destination);
    }

    public int copyTo(InputStream source, File destination) {
        return getMachine().copyTo(source, destination);
    }

}
