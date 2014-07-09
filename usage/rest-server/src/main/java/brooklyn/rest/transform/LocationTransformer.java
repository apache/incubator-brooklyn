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
package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContext;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;

public class LocationTransformer {

    private static final Logger log = LoggerFactory.getLogger(LocationTransformer.LocationDetailLevel.class);
    
    public static enum LocationDetailLevel { NONE, LOCAL_EXCLUDING_SECRET, FULL_EXCLUDING_SECRET, FULL_INCLUDING_SECRET }
    
    /** @deprecated since 0.7.0 use method taking management context and detail specifier */
    @Deprecated
    public static LocationSummary newInstance(String id, LocationSpec locationSpec) {
        return newInstance(null, id, locationSpec, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
    }
    public static LocationSummary newInstance(ManagementContext mgmt, String id, LocationSpec locationSpec, LocationDetailLevel level) {
        // TODO: Remove null checks on mgmt when newInstance(String, LocationSpec) is deleted
        Map<String, ?> config = locationSpec.getConfig();
        if (mgmt != null && (level==LocationDetailLevel.FULL_EXCLUDING_SECRET || level==LocationDetailLevel.FULL_INCLUDING_SECRET)) {
            LocationDefinition ld = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
            Location ll = mgmt.getLocationRegistry().resolveForPeeking(ld);
            if (ll!=null) config = ll.getAllConfig(true);
        } else if (level==LocationDetailLevel.LOCAL_EXCLUDING_SECRET) {
            // get displayName
            if (!config.containsKey(LocationConfigKeys.DISPLAY_NAME.getName()) && mgmt!=null) {
                LocationDefinition ld = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
                Location ll = mgmt.getLocationRegistry().resolveForPeeking(ld);
                if (ll!=null) {
                    Map<String, Object> configExtra = ll.getAllConfig(true);
                    if (configExtra.containsKey(LocationConfigKeys.DISPLAY_NAME.getName())) {
                        ConfigBag configNew = ConfigBag.newInstance(config);
                        configNew.configure(LocationConfigKeys.DISPLAY_NAME, (String)configExtra.get(LocationConfigKeys.DISPLAY_NAME.getName()));
                        config = configNew.getAllConfig();
                    }
                }
            }
        }
        return new LocationSummary(
                id,
                locationSpec.getName(),
                locationSpec.getSpec(),
                null,
                copyConfig(config, level),
                ImmutableMap.of("self", URI.create("/v1/locations/" + id)));
    }

    /** @deprecated since 0.7.0 use method taking management context and detail specifier */
    @Deprecated
    public static LocationSummary newInstance(LocationDefinition l) {
        return newInstance(null, l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
    }

    public static LocationSummary newInstance(ManagementContext mgmt, LocationDefinition l, LocationDetailLevel level) {
        // TODO: Can remove null checks on mgmt when newInstance(LocationDefinition) is deleted
        Map<String, Object> config = l.getConfig();
        if (mgmt != null && (level==LocationDetailLevel.FULL_EXCLUDING_SECRET || level==LocationDetailLevel.FULL_INCLUDING_SECRET)) {
            Location ll = mgmt.getLocationRegistry().resolveForPeeking(l);
            if (ll!=null) config = ll.getAllConfig(true);
        } else if (level==LocationDetailLevel.LOCAL_EXCLUDING_SECRET) {
            // get displayName
            if (mgmt != null && !config.containsKey(LocationConfigKeys.DISPLAY_NAME.getName())) {
                Location ll = mgmt.getLocationRegistry().resolveForPeeking(l);
                if (ll!=null) {
                    Map<String, Object> configExtra = ll.getAllConfig(true);
                    if (configExtra.containsKey(LocationConfigKeys.DISPLAY_NAME.getName())) {
                        ConfigBag configNew = ConfigBag.newInstance(config);
                        configNew.configure(LocationConfigKeys.DISPLAY_NAME, (String)configExtra.get(LocationConfigKeys.DISPLAY_NAME.getName()));
                        config = configNew.getAllConfig();
                    }
                }
            }
        }

        return new LocationSummary(
                l.getId(),
                l.getName(),
                l.getSpec(),
                null,
                copyConfig(config, level),
                ImmutableMap.of("self", URI.create("/v1/locations/" + l.getId())));
    }

    private static Map<String, ?> copyConfig(Map<String,?> entries, LocationDetailLevel level) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        if (level!=LocationDetailLevel.NONE) {
            for (Map.Entry<String,?> entry : entries.entrySet()) {
                if (level==LocationDetailLevel.FULL_INCLUDING_SECRET || !Entities.isSecret(entry.getKey())) {
                    builder.put(entry.getKey(), WebResourceUtils.getValueForDisplay(entry.getValue(), true, false));
                }
            }
        }
        return builder.build();
    }

    public static LocationSummary newInstance(ManagementContext mgmt, Location l, LocationDetailLevel level) {
        String spec = null;
        String specId = null;
        Location lp = l;
        while (lp!=null && (spec==null || specId==null)) {
            // walk parent locations
            // TODO not sure this is the best strategy, or if it's needed, as the spec config is inherited anyway... 
            if (spec==null)
                spec = Strings.toString( lp.getAllConfig(true).get(LocationInternal.ORIGINAL_SPEC.getName()) );
            if (specId==null) {
                LocationDefinition ld = null;
                // prefer looking it up by name as this loads the canonical definition
                if (spec!=null) ld = mgmt.getLocationRegistry().getDefinedLocationByName(spec);
                if (ld==null && spec!=null && spec.startsWith("named:")) 
                    ld = mgmt.getLocationRegistry().getDefinedLocationByName(Strings.removeFromStart(spec, "named:"));
                if (ld==null) ld = mgmt.getLocationRegistry().getDefinedLocationById(lp.getId());
                if (ld!=null) {
                    if (spec==null) spec = ld.getSpec();
                    specId = ld.getId();
                }
            }
            lp = lp.getParent();
        }
        if (specId==null && spec!=null) {
            // fall back to attempting to lookup it
            Location ll = mgmt.getLocationRegistry().resolveIfPossible(spec);
            if (ll!=null) specId = ll.getId();
        }
        
        Map<String, Object> configOrig = l.getAllConfig(level!=LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
        if (level==LocationDetailLevel.LOCAL_EXCLUDING_SECRET) {
            // for LOCAL, also get the display name
            if (!configOrig.containsKey(LocationConfigKeys.DISPLAY_NAME.getName())) {
                Map<String, Object> configExtra = l.getAllConfig(true);
                if (configExtra.containsKey(LocationConfigKeys.DISPLAY_NAME.getName()))
                    configOrig.put(LocationConfigKeys.DISPLAY_NAME.getName(), configExtra.get(LocationConfigKeys.DISPLAY_NAME.getName()));
            }
        }
        Map<String, ?> config = level!=LocationDetailLevel.NONE ? null : copyConfig(configOrig, level);
        
        return new LocationSummary(
            l.getId(),
            l.getDisplayName(),
            spec,
            l.getClass().getName(),
            config,
            MutableMap.of("self", URI.create("/v1/locations/" + l.getId()))
                .addIfNotNull("parent", l.getParent()!=null ? URI.create("/v1/locations/"+l.getParent().getId()) : null)
                .addIfNotNull("spec", specId!=null ? URI.create("/v1/locations/"+specId) : null)
                .toImmutable() );
    }
    
}
