/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.launcher;

import brooklyn.management.ManagementContext;

public class BrooklynServerDetails {

    protected BrooklynWebServer webServer;
    protected ManagementContext mgmtContext;
    
    public BrooklynServerDetails(BrooklynWebServer webServer, ManagementContext mgmtContext) {
        super();
        this.webServer = webServer;
        this.mgmtContext = mgmtContext;
    }

    public BrooklynWebServer getWebServer() {
        return webServer;
    }
    
    public String getWebServerUrl() {
        if (webServer==null) return null;
        return webServer.getRootUrl();
    }
    
    public ManagementContext getManagementContext() {
        return mgmtContext;
    }
    
}
