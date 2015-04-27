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
package brooklyn.entity.database.sevenzip;

import java.io.File;
import java.util.List;

import brooklyn.entity.basic.AbstractSoftwareProcessWinRmDriver;
import brooklyn.location.basic.WinRmMachineLocation;

import com.google.common.collect.ImmutableList;

public class SevenZipWinRmDriver extends AbstractSoftwareProcessWinRmDriver implements SevenZipDriver {

    public SevenZipWinRmDriver(SevenZipNodeImpl entity, WinRmMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void install() {
        copyTo(new File("/tmp/feather.png"), new File("C:\\Users\\Administrator\\feather.png"));
        List<String> commands = ImmutableList.of(
                "(new-object System.Net.WebClient).DownloadFile(\"http://www.7-zip.org/a/7z938-x64.msi\", \"setup.msi\")",
                "Start-Process msiexec.exe -ArgumentList \"/i setup.msi /qn\" -Wait"
        );
        executePowerShell(commands);
    }

    @Override
    public void customize() {

    }

    @Override
    public void launch() {

    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void stop() {
        List<String> commands = ImmutableList.of(
                "Start-Process msiexec.exe -ArgumentList \"/x c:\\setup.msi /qn\" -Wait"
        );
        executePowerShell(commands);
    }

}
