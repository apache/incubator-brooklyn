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
package org.apache.brooklyn.entity.nosql.mongodb;

import org.apache.brooklyn.util.text.Strings;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.entity.Entity;


public class MongoDBAuthenticationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBAuthenticationUtils.class);

    private MongoDBAuthenticationUtils(){}

    /**
     * @return true if either the keyfile contents or keyfile url is set, false otherwise. If both are set, an IllegalStateException is thrown
     */
    public static boolean usesAuthentication(Entity entity) {
        String keyfileContents = entity.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_CONTENTS);
        String keyfileUrl = entity.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_URL);
        return Strings.isNonBlank(keyfileContents) || Strings.isNonBlank(keyfileUrl);
    }

    public static String getRootPassword(Entity entity) {
        String password = entity.config().get(MongoDBAuthenticationMixins.ROOT_PASSWORD);
        if (Strings.isEmpty(password)) {
            LOG.debug(entity + " has no password specified for " + MongoDBAuthenticationMixins.ROOT_PASSWORD.getName() + "; using a random string");
            password = Strings.makeRandomId(16);
            entity.sensors().set(MongoDBAuthenticationMixins.ROOT_PASSWORD, password);
            entity.config().set(MongoDBAuthenticationMixins.ROOT_PASSWORD, password);
        }
        return password;
    }

    /**
     * Configures the {@code spec} with authentication configuration from {@code source}
     */
    public static void setAuthenticationConfig(EntitySpec<?> spec, Entity source) {
        if (MongoDBAuthenticationUtils.usesAuthentication(source)) {
            spec.configure(MongoDBAuthenticationMixins.MONGODB_KEYFILE_CONTENTS, source.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_CONTENTS));
            spec.configure(MongoDBAuthenticationMixins.MONGODB_KEYFILE_URL, source.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_URL));
            spec.configure(MongoDBAuthenticationMixins.ROOT_USERNAME, source.config().get(MongoDBAuthenticationMixins.ROOT_USERNAME));
            spec.configure(MongoDBAuthenticationMixins.ROOT_PASSWORD, getRootPassword(source));
        }
    }

    /**
     * Configures the {@code spec} with authentication configuration from {@code source}
     */
    public static void setAuthenticationConfig(Entity entity, Entity source) {
        if (MongoDBAuthenticationUtils.usesAuthentication(source)) {
            entity.config().set(MongoDBAuthenticationMixins.MONGODB_KEYFILE_CONTENTS, source.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_CONTENTS));
            entity.config().set(MongoDBAuthenticationMixins.MONGODB_KEYFILE_URL, source.config().get(MongoDBAuthenticationMixins.MONGODB_KEYFILE_URL));
            entity.config().set(MongoDBAuthenticationMixins.ROOT_USERNAME, source.config().get(MongoDBAuthenticationMixins.ROOT_USERNAME));
            entity.config().set(MongoDBAuthenticationMixins.ROOT_PASSWORD, getRootPassword(source));
        }
    }
}
