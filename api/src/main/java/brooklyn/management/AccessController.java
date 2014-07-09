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
package brooklyn.management;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.annotations.Beta;

@Beta
public interface AccessController {

    // TODO Expect this class' methods to change, e.g. including the user doing the
    // provisioning or the provisioning parameters such as jurisdiction
    
    public static class Response {
        private static final Response ALLOWED = new Response(true, "");
        
        public static Response allowed() {
            return ALLOWED;
        }
        
        public static Response disallowed(String msg) {
            return new Response(false, msg);
        }
        
        private final boolean allowed;
        private final String msg;

        private Response(boolean allowed, String msg) {
            this.allowed = allowed;
            this.msg = msg;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getMsg() {
            return msg;
        }
    }

    public Response canProvisionLocation(Location provisioner);

    public Response canManageLocation(Location loc);
    
    public Response canManageEntity(Entity entity);
}
