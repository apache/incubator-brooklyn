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
package brooklyn.location.cloud.names;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

/** Provides a machine namer which looks at a location config key {@link #MACHINE_NAME_TEMPLATE}
 * to construct the hostname.
 * For instance, setting this to <code>${config.entity_hostname}</code>
 * will take the hostname from an <code>entity_hostname</code> key passed as entity <code>brooklyn.config</code>.
 * <p>
 * Note that this is not jclouds aware, so jclouds-specific cloud max lengths are not observed with this class.
 */
public class CustomMachineNamer extends BasicCloudMachineNamer {
    
    public static final ConfigKey<String> MACHINE_NAME_TEMPLATE = ConfigKeys.newStringConfigKey("custom.machine.namer.machine", 
            "Freemarker template format for custom machine name", "${entity.displayName}");
    @SuppressWarnings("serial")
    public static final ConfigKey<Map<String, ?>> EXTRA_SUBSTITUTIONS = ConfigKeys.newConfigKey(new TypeToken<Map<String, ?>>() {}, 
            "custom.machine.namer.substitutions", "Additional substitutions to be used in the template", ImmutableMap.<String, Object>of());
    
    @Override
    protected String generateNewIdOfLength(ConfigBag setup, int len) {
        Object context = setup.peek(CloudLocationConfig.CALLER_CONTEXT);
        Entity entity = null;
        if (context instanceof Entity) {
            entity = (Entity) context;
        }
        
        String template = setup.get(MACHINE_NAME_TEMPLATE);
        
        String processed;
        if (entity == null) {
            processed = TemplateProcessor.processTemplateContents(template, setup.get(EXTRA_SUBSTITUTIONS));
        } else {
            processed = TemplateProcessor.processTemplateContents(template, (EntityInternal)entity, setup.get(EXTRA_SUBSTITUTIONS));
        }
        
        processed = Strings.removeFromStart(processed, "#ftl\n");
        
        return sanitize(processed);
    }
    
}
