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
package org.apache.brooklyn.entity.webapp.tomcat;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Tomcat instance.
 */
@Catalog(name="Tomcat Server",
        description="Apache Tomcat is an open source software implementation of the Java Servlet and JavaServer Pages technologies",
        iconUrl="classpath:///tomcat-logo.png")
@ImplementedBy(Tomcat8ServerImpl.class)
public interface Tomcat8Server extends TomcatServer {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "8.0.22");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://download.nextag.com/apache/tomcat/tomcat-8/v${version}/bin/apache-tomcat-${version}.tar.gz");

    @SetFromFlag("server.xml")
    ConfigKey<String> SERVER_XML_RESOURCE = ConfigKeys.newStringConfigKey(
            "tomcat.serverxml", "The file to template and use as the Tomcat process' server.xml",
            JavaClassNames.resolveClasspathUrl(Tomcat8Server.class, "tomcat8-server.xml"));

    @SetFromFlag("web.xml")
    ConfigKey<String> WEB_XML_RESOURCE = ConfigKeys.newStringConfigKey(
            "tomcat.webxml", "The file to template and use as the Tomcat process' web.xml",
            JavaClassNames.resolveClasspathUrl(Tomcat8Server.class, "tomcat8-web.xml"));
}
