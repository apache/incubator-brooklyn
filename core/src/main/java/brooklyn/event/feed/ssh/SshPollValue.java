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
package brooklyn.event.feed.ssh;

import javax.annotation.Nullable;

import brooklyn.location.basic.SshMachineLocation;

public class SshPollValue {

    private final SshMachineLocation machine;
    private final int exitStatus;
    private final String stdout;
    private final String stderr;

    public SshPollValue(SshMachineLocation machine, int exitStatus, String stdout, String stderr) {
        this.machine = machine;
        this.exitStatus = exitStatus;
        this.stdout = stdout;
        this.stderr = stderr;
    }
    
    /** The machine the command will run on. */
    public SshMachineLocation getMachine() {
        return machine;
    }

    /** Command exit status, or -1 if error is set. */
    public int getExitStatus() {
        return exitStatus;
    }

    /** Command standard output; may be null if no content available. */
    @Nullable
    public String getStdout() {
        return stdout;
    }

    /** Command standard error; may be null if no content available. */
    @Nullable
    public String getStderr() {
        return stderr;
    }
}
