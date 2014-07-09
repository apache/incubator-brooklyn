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
// TODO Untested code; see brooklyn-example for better maintained examples!
public class TomcatFabricApp extends AbstractApplication {
    @Override
    public void init() {
        addChild(EntitySpec.create(DynamicFabric.class)
                .configure("displayName", "WebFabric")
                .configure("displayNamePrefix", "")
                .configure("displayNameSuffix", " web cluster")
                .configure("memberSpec", EntitySpec.create(ControlledDynamicWebAppCluster.class)
                        .configure("initialSize", 2)
                        .configure("memberSpec", : EntitySpec.create(TomcatServer.class)
                                .configure("httpPort", "8080+")
                                .configure("war", "/path/to/booking-mvc.war"))));
    }
}
