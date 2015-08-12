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
package org.apache.brooklyn.entity.webapp;

import java.util.Map;

import brooklyn.util.guava.Maybe;

public class HttpsSslConfig {

    private String keystoreUrl;
    private String keystorePassword;
    private String keyAlias;
    
    public HttpsSslConfig() {
    }
    
    public HttpsSslConfig keystoreUrl(String val) {
        keystoreUrl = val; return this;
    }
    
    public HttpsSslConfig keystorePassword(String val) {
        keystorePassword = val; return this;
    }
    
    public HttpsSslConfig keyAlias(String val) {
        keyAlias = val; return this;
    }
    
    public String getKeystoreUrl() {
        return keystoreUrl;
    }
    
    public String getKeystorePassword() {
        return keystorePassword;
    }
    
    public String getKeyAlias() {
        return keyAlias;
    }

    // method naming convention allows it to be used by TypeCoercions
    public static HttpsSslConfig fromMap(Map<String,String> map) {
        HttpsSslConfig result = new HttpsSslConfig();
        result.keystoreUrl = first(map, "keystoreUrl", "url").orNull();
        result.keystorePassword = first(map, "keystorePassword", "password").orNull();
        result.keyAlias = first(map, "keyAlias", "alias", "key").orNull();
        return result;
    }

    private static Maybe<String> first(Map<String,String> map, String ...keys) {
        for (String key: keys) {
            if (map.containsKey(key))
                return Maybe.of(map.get(key));
        }
        return Maybe.<String>absent();
    }
}
