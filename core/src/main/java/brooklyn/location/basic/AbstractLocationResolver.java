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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocationResolver.SpecParser.ParsedSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.KeyValueParser;

import com.google.common.collect.ImmutableList;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>byon(hosts=myhost)
 *     <li>byon(hosts=myhost,myhost2)
 *     <li>byon(hosts="myhost, myhost2")
 *     <li>byon(hosts=myhost,myhost2, name=abc)
 *     <li>byon(hosts="myhost, myhost2", name="my location name")
 *   </ul>
 * 
 * @author aled
 */
@SuppressWarnings({"unchecked","rawtypes"})
public abstract class AbstractLocationResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(AbstractLocationResolver.class);
    
    protected volatile ManagementContext managementContext;

    protected volatile SpecParser specParser;

    protected abstract Class<? extends Location> getLocationType();
    
    protected abstract SpecParser getSpecParser();
    
    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.specParser = getSpecParser();
    }
    
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        ConfigBag config = extractConfig(locationFlags, spec, registry);
        Map globalProperties = registry.getProperties();
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        
        if (registry != null) {
            LocationPropertiesFromBrooklynProperties.setLocalTempDir(globalProperties, config);
        }

        return managementContext.getLocationManager().createLocation(LocationSpec.create(getLocationType())
            .configure(config.getAllConfig())
            .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }

    protected ConfigBag extractConfig(Map<?,?> locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        Map globalProperties = registry.getProperties();
        ParsedSpec parsedSpec = specParser.parse(spec);
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        
        // prefer args map over location flags
        Map<String, Object> filteredProperties = getFilteredLocationProperties(getPrefix(), namedLocation, globalProperties);
        ConfigBag flags = ConfigBag.newInstance(parsedSpec.argsMap).putIfAbsent(locationFlags).putIfAbsent(filteredProperties);

        return flags;
    }
    
    protected Map<String, Object> getFilteredLocationProperties(String provider, String namedLocation, Map<String, ?> globalProperties) {
        return new LocationPropertiesFromBrooklynProperties().getLocationProperties(getPrefix(), namedLocation, globalProperties);
    }
    
    /**
     * Parses a spec, by default of the general form "prefix:parts1:part2(arg1=val1,arg2=val2)"
     */
    protected static class SpecParser {
        
        protected static class ParsedSpec {
            public final String spec;
            public final List<String> partsList;
            public final Map<String,String> argsMap;
            
            ParsedSpec(String spec, List<String> partsList, Map<String,String> argsMap) {
                this.spec = spec;
                this.partsList = ImmutableList.copyOf(partsList);
                this.argsMap = Collections.unmodifiableMap(MutableMap.copyOf(argsMap));
            }
        }
        
        protected final String prefix;
        protected final Pattern pattern;
        private String exampleUsage;
        
        public SpecParser(String prefix) {
            this.prefix = prefix;
            pattern = Pattern.compile("("+prefix.toLowerCase()+"|"+prefix.toUpperCase()+")" + "(:)?" + "(\\((.*)\\))?$");
        }
        
        public SpecParser(String prefix, Pattern pattern) {
            this.prefix = prefix;
            this.pattern = pattern;
        }
        
        public SpecParser setExampleUsage(String exampleUsage) {
            this.exampleUsage = exampleUsage;
            return this;
        }

        protected String getUsage(String spec) {
            if (exampleUsage == null) {
                return "Spec should be in the form "+pattern;
            } else {
                return "for example, "+exampleUsage;
            }
        }
        
        protected void checkParsedSpec(ParsedSpec parsedSpec) {
            // If someone tries "byon:(),byon:()" as a single spec, we get weird key-values!
            for (String key : parsedSpec.argsMap.keySet()) {
                if (key.contains(":") || key.contains("{") || key.contains("}") || key.contains("(") || key.contains(")")) {
                    throw new IllegalArgumentException("Invalid byon spec: "+parsedSpec.spec+" (key="+key+")");
                }
            }
            String name = parsedSpec.argsMap.get("name");
            if (parsedSpec.argsMap.containsKey("name") && (name == null || name.isEmpty())) {
                throw new IllegalArgumentException("Invalid location '"+parsedSpec.spec+"'; if name supplied then value must be non-empty");
            }
            String displayName = parsedSpec.argsMap.get("displayName");
            if (parsedSpec.argsMap.containsKey("displayName") && (displayName == null || displayName.isEmpty())) {
                throw new IllegalArgumentException("Invalid location '"+parsedSpec.spec+"'; if displayName supplied then value must be non-empty");
            }
        }
        
        public ParsedSpec parse(String spec) {
            Matcher matcher = pattern.matcher(spec);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid location '"+spec+"'; "+getUsage(spec));
            }
            
            String argsPart = matcher.group(3);
            if (argsPart != null && argsPart.startsWith("(") && argsPart.endsWith(")")) {
                // TODO Hacky; hosts("1.1.1.1") returns argsPart=("1.1.1.1")
                argsPart = argsPart.substring(1, argsPart.length()-1);
            }
            Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
            ParsedSpec result = new ParsedSpec(spec, ImmutableList.<String>of(), argsMap);
            checkParsedSpec(result);
            return result;
        }
    }
}
