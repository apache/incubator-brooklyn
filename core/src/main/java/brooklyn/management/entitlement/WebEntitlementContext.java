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
package brooklyn.management.entitlement;

import brooklyn.util.javalang.JavaClassNames;

/** indicates an authenticated web request as the entitlements context;
 * note user may still be null if no authentication was requested */
public class WebEntitlementContext implements EntitlementContext {

    final String user;
    final String sourceIp;
    final String requestUri;
    
    /** a mostly-unique identifier for the inbound request, to distinguish between duplicate requests and for cross-referencing with URI's */
    final String requestUniqueIdentifier;
    
    public WebEntitlementContext(String user, String sourceIp, String requestUri, String requestUniqueIdentifier) {
        this.user = user;
        this.sourceIp = sourceIp;
        this.requestUri = requestUri;
        this.requestUniqueIdentifier = requestUniqueIdentifier;
    }
    
    @Override public String user() { return user; }
    public String sourceIp() { return sourceIp; }
    public String requestUri() { return requestUri; }
    public String requestUniqueIdentifier() { return requestUniqueIdentifier; }

    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(getClass())+"["+user+"@"+sourceIp+":"+requestUniqueIdentifier+"]";
    }
}
