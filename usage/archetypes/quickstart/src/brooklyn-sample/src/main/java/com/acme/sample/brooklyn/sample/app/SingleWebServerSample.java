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
package com.acme.sample.brooklyn.sample.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.maven.MavenArtifact;
import brooklyn.util.maven.MavenRetriever;

/** This example starts one web app on 8080. */
public class SingleWebServerSample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerSample.class);

    public static final String DEFAULT_WAR_URL =
            // can supply any URL -- this loads a stock example from maven central / sonatype
            MavenRetriever.localUrl(MavenArtifact.fromCoordinate("io.brooklyn.example:brooklyn-example-hello-world-sql-webapp:war:0.5.0"));

    /** Initialize our application. In this case it consists of 
     *  a single JBoss entity, configured to run the WAR above. */
    @Override
    public void init() {
        addChild(EntitySpec.create(JBoss7Server.class)
                .configure(JavaWebAppService.ROOT_WAR, DEFAULT_WAR_URL)
                .configure(Attributes.HTTP_PORT, PortRanges.fromString("8080+")));
    }

}
