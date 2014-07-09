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
package brooklyn.entity.chef;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.INSTALL_UNZIP;
import static brooklyn.util.ssh.BashCommands.downloadToStdout;
import static brooklyn.util.ssh.BashCommands.sudo;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.Beta;

/** BASH commands useful for setting up Chef */
@Beta
public class ChefBashCommands {

    public static final String INSTALL_FROM_OPSCODE =
            BashCommands.chain(
                    INSTALL_CURL,
                    INSTALL_TAR,
                    INSTALL_UNZIP,
                    "( "+downloadToStdout("https://www.opscode.com/chef/install.sh") + " | " + sudo("bash")+" )");

}
