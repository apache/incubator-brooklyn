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
package brooklyn.entity.webapp;

import java.util.Set;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public interface WebAppServiceConstants extends WebAppServiceMetrics {

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;

    @SetFromFlag("httpsPort")
    public static final PortAttributeSensorAndConfigKey HTTPS_PORT = Attributes.HTTPS_PORT;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("enabledProtocols")
    public static final BasicAttributeSensorAndConfigKey<Set<String>> ENABLED_PROTOCOLS = new BasicAttributeSensorAndConfigKey(
            Set.class, "webapp.enabledProtocols", "List of enabled protocols (e.g. http, https)", ImmutableList.of("http"));

    @SetFromFlag("httpsSsl")
    public static final BasicAttributeSensorAndConfigKey<HttpsSslConfig> HTTPS_SSL_CONFIG = new BasicAttributeSensorAndConfigKey<HttpsSslConfig>(
            HttpsSslConfig.class, "webapp.https.ssl", "SSL Configuration for HTTPS", null);
    
    public static final AttributeSensor<String> ROOT_URL = RootUrl.ROOT_URL;

}

// this class is added because the ROOT_URL relies on a static initialization which unfortunately can't be added to an interface.
class RootUrl {
    public static final AttributeSensor<String> ROOT_URL = Sensors.newStringSensor("webapp.url", "URL");

    static {
        RendererHints.register(ROOT_URL, new RendererHints.NamedActionWithUrl("Open"));
    }
}
