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

import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.java.UsesJava;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface JavaWebAppService extends WebAppService, UsesJava {

	@SetFromFlag("war")
	public static final ConfigKey<String> ROOT_WAR = new BasicConfigKey<String>(
	        String.class, "wars.root", "WAR file to deploy as the ROOT, as URL (supporting file: and classpath: prefixes)");

    @SetFromFlag("wars")
	public static final ConfigKey<List<String>> NAMED_WARS = new BasicConfigKey(
	        List.class, "wars.named", "Archive files to deploy, as URL strings (supporting file: and classpath: prefixes); context (path in user-facing URL) will be inferred by name");
    
    @SetFromFlag("warsByContext")
    public static final ConfigKey<Map<String,String>> WARS_BY_CONTEXT = new BasicConfigKey(
            Map.class, "wars.by.context", "Map of context keys (path in user-facing URL, typically without slashes) to archives (e.g. WARs by URL) to deploy, supporting file: and classpath: prefixes)");
}
