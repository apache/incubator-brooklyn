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
package org.apache.brooklyn.demo;

import static org.apache.brooklyn.sensor.core.DependentConfiguration.attributeWhenReady;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.nosql.redis.RedisStore;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.nodejs.NodeJsWebAppService;
import org.apache.brooklyn.sensor.core.DependentConfiguration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Node.JS Todo Application
 */
@Catalog(name="NodeJS Todo",
        description="Node.js is a cross-platform runtime environment for server-side and networking applications. Node.js applications are written in JavaScript",
        iconUrl="classpath://nodejs-logo.png")
public class NodeJsTodoApplication extends AbstractApplication implements StartableApplication {

    @Override
    public void initApp() {
        RedisStore redis = addChild(EntitySpec.create(RedisStore.class));

        addChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure(NodeJsWebAppService.APP_GIT_REPOSITORY_URL, "https://github.com/grkvlt/nodejs-todo/")
                .configure(NodeJsWebAppService.APP_FILE, "server.js")
                .configure(NodeJsWebAppService.APP_NAME, "nodejs-todo")
                .configure(NodeJsWebAppService.NODE_PACKAGE_LIST, ImmutableList.of("express", "ejs", "jasmine-node", "underscore", "method-override", "cookie-parser", "express-session", "body-parser", "cookie-session", "redis", "redis-url", "connect"))
                .configure(SoftwareProcess.SHELL_ENVIRONMENT, ImmutableMap.<String, Object>of(
                        "REDISTOGO_URL", DependentConfiguration.formatString("redis://%s:%d/",
                                attributeWhenReady(redis, Attributes.HOSTNAME), attributeWhenReady(redis, RedisStore.REDIS_PORT))))
                .configure(SoftwareProcess.LAUNCH_LATCH, attributeWhenReady(redis, Startable.SERVICE_UP)));
    }

}
