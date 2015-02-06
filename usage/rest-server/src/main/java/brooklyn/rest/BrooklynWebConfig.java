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
package brooklyn.rest;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.config.ConfigPredicates;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.PortRange;
import brooklyn.rest.security.provider.DelegatingSecurityProvider;
import brooklyn.rest.security.provider.ExplicitUsersSecurityProvider;

public class BrooklynWebConfig {

    public final static String BASE_NAME = "brooklyn.webconsole";
    public final static String BASE_NAME_SECURITY = BASE_NAME+".security";

    /**
     * The security provider to be loaded by {@link DelegatingSecurityProvider}.
     * e.g. <code>brooklyn.webconsole.security.provider=brooklyn.rest.security.provider.AnyoneSecurityProvider</code>
     * will allow anyone to log in.
     */
    public final static ConfigKey<String> SECURITY_PROVIDER_CLASSNAME = ConfigKeys.newStringConfigKey(
            BASE_NAME_SECURITY+".provider", "class name of a Brooklyn SecurityProvider",
            ExplicitUsersSecurityProvider.class.getCanonicalName());
    
    /**
     * Explicitly set the users/passwords, e.g. in brooklyn.properties:
     * brooklyn.webconsole.security.users=admin,bob
     * brooklyn.webconsole.security.user.admin.password=password
     * brooklyn.webconsole.security.user.bob.password=bobspass
     */
    public final static ConfigKey<String> USERS = ConfigKeys.newStringConfigKey(BASE_NAME_SECURITY+".users");

    public final static ConfigKey<String> PASSWORD_FOR_USER(String user) {
        return ConfigKeys.newStringConfigKey(BASE_NAME_SECURITY + ".user." + user + ".password");
    }
    
    public final static ConfigKey<String> SALT_FOR_USER(String user) {
        return ConfigKeys.newStringConfigKey(BASE_NAME_SECURITY + ".user." + user + ".salt");
    }
    
    public final static ConfigKey<String> SHA256_FOR_USER(String user) {
        return ConfigKeys.newStringConfigKey(BASE_NAME_SECURITY + ".user." + user + ".sha256");
    }
    
    public final static ConfigKey<String> LDAP_URL = ConfigKeys.newStringConfigKey(
            BASE_NAME_SECURITY+".ldap.url");

    public final static ConfigKey<String> LDAP_REALM = ConfigKeys.newStringConfigKey(
            BASE_NAME_SECURITY+".ldap.realm");

    public final static ConfigKey<Boolean> HTTPS_REQUIRED = ConfigKeys.newBooleanConfigKey(
            BASE_NAME+".security.https.required",
            "Whether HTTPS is required; false here can be overridden by CLI option", false); 

    public final static ConfigKey<PortRange> WEB_CONSOLE_PORT = ConfigKeys.newConfigKey(PortRange.class,
        BASE_NAME+".port",
        "Port/range for the web console to listen on; can be overridden by CLI option");

    public final static ConfigKey<String> KEYSTORE_URL = ConfigKeys.newStringConfigKey(
            BASE_NAME+".security.keystore.url",
            "Keystore from which to take the certificate to present when running HTTPS; "
            + "note that normally the password is also required, and an alias for the certificate if the keystore has more than one");

    public final static ConfigKey<String> KEYSTORE_PASSWORD = ConfigKeys.newStringConfigKey(
            BASE_NAME+".security.keystore.password",
            "Password for the "+KEYSTORE_URL);

    public final static ConfigKey<String> KEYSTORE_CERTIFICATE_ALIAS = ConfigKeys.newStringConfigKey(
            BASE_NAME+".security.keystore.certificate.alias",
            "Alias in "+KEYSTORE_URL+" for the certificate to use; defaults to the first if not supplied");

    public final static boolean hasNoSecurityOptions(ConfigMap config) {
        return config.submap(ConfigPredicates.startingWith(BASE_NAME_SECURITY)).isEmpty();
    }
    
}
