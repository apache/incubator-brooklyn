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
package brooklyn.config;

import brooklyn.management.ManagementContext;
import brooklyn.util.internal.StringSystemProperty;

/** attributes which callers can set and a service application
 * (such as servlet or osgi) will pay attention to,
 * contained in one place for convenience
 * 
 * @author alex
 */
public class BrooklynServiceAttributes {

    /*
     * These fields are contained here so that they are visible both to web console
     * and to launcher, without needing a separate web-console-support project,
     * or battling maven etc to build web-console as jar available to launcher
     * (which would contain a lot of crap as well).
     */
	
    /** used to hold the instance of ManagementContext which should be used */
    public static final String BROOKLYN_MANAGEMENT_CONTEXT = ManagementContext.class.getName();
    
    /** poor-man's security, to specify a user to be automatically logged in
     * (e.g. to bypass security, during dev/test); 'admin' is usually a sensible choice.
     * if not specified (the default) username+password is required. 
     * @deprecated since 0.6.0; not used; you can now configure security properly instead!
     * (though this may be useful again when we have users and permissions,
     * to indicate the user who should be logged in by default...) */ @Deprecated
    public static final String BROOKLYN_AUTOLOGIN_USERNAME = "brooklyn.autologin.username";
    
    /** poor-man's security, to specify a default password for access 
     * @deprecated since 0.6.0; not used; you can now configure security properly instead! */ @Deprecated
    public static final String BROOKLYN_DEFAULT_PASSWORD = "brooklyn.default.password";

    // TODO use ConfigKey (or possibly BrooklynSystemProperties ?)
    
    /** in some cases localhost does not resolve correctly 
     * (e.g. to an interface which is defined locally but not in operation,
     * or where multiple NICs are available and java's InetAddress.getLocalHost() strategy is not doing what is desired);
     * use this to supply a specific address (e.g. "127.0.0.1" or a specific IP on a specific NIC or FW)
     */
    public static StringSystemProperty LOCALHOST_IP_ADDRESS = new StringSystemProperty("brooklyn.location.localhost.address");
    
}
