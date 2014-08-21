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
package brooklyn.entity.webapp.nodejs;

import java.util.List;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@Catalog(name="Node.JS Application",
        description="Node.JS Web Application",
        iconUrl="classpath:///nodejs-logo.png")
@ImplementedBy(NodeJsWebAppServiceImpl.class)
public interface NodeJsWebAppService extends SoftwareProcess, WebAppService {

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "stable");

    @SetFromFlag("httpPort")
    ConfigKey<PortRange> HTTP_PORT = ConfigKeys.newConfigKeyWithDefault(Attributes.HTTP_PORT.getConfigKey(), PortRanges.fromInteger(3000));

    @SetFromFlag("gitRepoUrl")
    ConfigKey<String> APP_GIT_REPOSITORY_URL = ConfigKeys.newStringConfigKey("nodejs.gitRepo.url", "The Git repository where the application is hosted");

    @SetFromFlag("archiveUrl")
    ConfigKey<String> APP_ARCHIVE_URL = ConfigKeys.newStringConfigKey("nodejs.archive.url", "The URL where the application archive is hosted");

    @SetFromFlag("appFileName")
    ConfigKey<String> APP_FILE = ConfigKeys.newStringConfigKey("nodejs.app.fileName", "The NodeJS application file to start", "app.js");

    @SetFromFlag("appName")
    ConfigKey<String> APP_NAME = ConfigKeys.newStringConfigKey("nodejs.app.name", "The name of the NodeJS application");

    @SetFromFlag("appCommand")
    ConfigKey<String> APP_COMMAND = ConfigKeys.newStringConfigKey("nodejs.app.command", "Command to start the NodeJS application (defaults to node)", "node");

    @SetFromFlag("appCommandLine")
    ConfigKey<String> APP_COMMAND_LINE = ConfigKeys.newStringConfigKey("nodejs.app.commandLine", "Replacement command line to start the NodeJS application (ignores command and file if set)");

    @SetFromFlag("nodePackages")
    ConfigKey<List<String>> NODE_PACKAGE_LIST = ConfigKeys.newConfigKey(new TypeToken<List<String>>() { },
            "nodejs.packages", "The NPM packages to install", ImmutableList.<String>of());

    ConfigKey<String> SERVICE_UP_PATH = ConfigKeys.newStringConfigKey("nodejs.serviceUp.path", "Path to use when checking the NodeJS application is running", "/");

    Integer getHttpPort();

}
