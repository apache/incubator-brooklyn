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
package org.apache.brooklyn.entity.proxy.nginx;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.proxy.AbstractController;
import org.apache.brooklyn.entity.proxy.ProxySslConfig;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

/**
 * This is a group whose members will be made available to a load-balancer / URL forwarding service (such as nginx).
 * Configuration requires a <b>domain</b> and some mechanism for finding members.
 * The easiest way to find members is using a <b>target</b> whose children will be tracked,
 * but alternative membership policies can also be used.
 */
@ImplementedBy(UrlMappingImpl.class)
public interface UrlMapping extends AbstractGroup {

    MethodEffector<Void> DISCARD = new MethodEffector<Void>(UrlMapping.class, "discard");

    @SetFromFlag("label")
    ConfigKey<String> LABEL = ConfigKeys.newStringConfigKey(
            "urlmapping.label", "optional human-readable label to identify a server");

    @SetFromFlag("domain")
    ConfigKey<String> DOMAIN = ConfigKeys.newStringConfigKey(
            "urlmapping.domain", "domain (hostname, e.g. www.foo.com) to present for this URL map rule; required.");

    @SetFromFlag("path")
    ConfigKey<String> PATH = ConfigKeys.newStringConfigKey(
            "urlmapping.path", "URL path (pattern) for this URL map rule. Currently only supporting regex matches "+
            "(if not supplied, will match all paths at the indicated domain)");

    @SetFromFlag("ssl")
    ConfigKey<ProxySslConfig> SSL_CONFIG = AbstractController.SSL_CONFIG;

    @SetFromFlag("rewrites")
    @SuppressWarnings("serial")
    ConfigKey<Collection<UrlRewriteRule>> REWRITES = ConfigKeys.newConfigKey(new TypeToken<Collection<UrlRewriteRule>>() { },
            "urlmapping.rewrites", "Set of URL rewrite rules to apply");

    @SetFromFlag("target")
    ConfigKey<Entity> TARGET_PARENT = ConfigKeys.newConfigKey(Entity.class,
            "urlmapping.target.parent", "optional target entity whose children will be pointed at by this mapper");

    @SuppressWarnings("serial")
    AttributeSensor<Collection<String>> TARGET_ADDRESSES = Sensors.newSensor(new TypeToken<Collection<String>>() { },
            "urlmapping.target.addresses", "set of addresses which should be forwarded to by this URL mapping");

    String getUniqueLabel();

    /** Adds a rewrite rule, must be called at config time. See {@link UrlRewriteRule} for more info. */
    UrlMapping addRewrite(String from, String to);

    /** Adds a rewrite rule, must be called at config time. See {@link UrlRewriteRule} for more info. */
    UrlMapping addRewrite(UrlRewriteRule rule);

    String getDomain();

    String getPath();

    Entity getTarget();

    void setTarget(Entity target);

    void recompute();

    Collection<String> getTargetAddresses();

    ProxySslConfig getSsl();

    @Effector(description="Unmanages the url-mapping, so it is discarded and no longer applies")
    void discard();
}
