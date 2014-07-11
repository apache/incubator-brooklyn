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
package brooklyn.entity.pool;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver.EnableableLocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.LocationInternal;
import brooklyn.location.basic.LocationPropertiesFromBrooklynProperties;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.Strings;

public class ServerPoolLocationResolver implements EnableableLocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolLocationResolver.class);
    private static final String PREFIX = "pool";
    public static final String POOL_SPEC = PREFIX + ":%s";
    private static final Pattern PATTERN = Pattern.compile("("+PREFIX+"|"+PREFIX.toUpperCase()+")" +
            ":([a-zA-Z0-9]+)" + // pool Id
            "(:\\((.*)\\))?$"); // arguments, e.g. displayName

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("name", "displayName");

    private ManagementContext managementContext;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving location '" + spec + "' with flags " + Joiner.on(",").withKeyValueSeparator("=").join(locationFlags));
        }
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());

        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            String m = String.format("Invalid location '%s'; must specify either %s:entityId or %s:entityId:(key=argument)",
                    spec, PREFIX, PREFIX);
            throw new IllegalArgumentException(m);
        }

        String argsPart = matcher.group(4);
        Map<String, String> argsMap = (argsPart != null) ? KeyValueParser.parseMap(argsPart) : Collections.<String,String>emptyMap();
        String displayNamePart = argsMap.get("displayName");
        String namePart = argsMap.get("name");

        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (argsMap.containsKey("displayName") && Strings.isEmpty(displayNamePart)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if displayName supplied then value must be non-empty");
        }
        if (argsMap.containsKey("name") && Strings.isEmpty(namePart)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties()
                .getLocationProperties(PREFIX, namedLocation, registry.getProperties());
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder()
                .putAll(filteredProperties)
                .putAll(locationFlags)
                .build();

        String poolId = matcher.group(2);
        if (Strings.isBlank(poolId)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; pool's entity id must be non-empty");
        }

        final String displayName = displayNamePart != null ? displayNamePart : "Server Pool " + poolId;
        final String locationName = namePart != null ? namePart : "serverpool-" + poolId;

        Entity pool = managementContext.getEntityManager().getEntity(poolId);
        LocationSpec<ServerPoolLocation> locationSpec = LocationSpec.create(ServerPoolLocation.class)
                .configure(flags)
                .configure(DynamicLocation.OWNER, pool)
                .configure(LocationInternal.NAMED_SPEC_NAME, locationName)
                .displayName(displayName);
        return managementContext.getLocationManager().createLocation(locationSpec);
    }

}
