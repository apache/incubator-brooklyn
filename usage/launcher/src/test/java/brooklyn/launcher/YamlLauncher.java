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
package brooklyn.launcher;

import brooklyn.launcher.camp.SimpleYamlLauncher;

public class YamlLauncher {

    public static void main(String[] args) {
        SimpleYamlLauncher l = new SimpleYamlLauncher();
        l.setShutdownAppsOnExit(true);
        
        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
//        l.launchAppYaml("java-web-app-and-memsql.yaml");
//        l.launchAppYaml("memsql.yaml");
//        l.launchAppYaml("classpath://mongo-blueprint.yaml");
    }

}
