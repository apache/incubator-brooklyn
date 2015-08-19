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
package org.apache.brooklyn.rest.resources;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.rest.api.LocationApi;
import org.apache.brooklyn.rest.domain.LocationSpec;
import org.apache.brooklyn.rest.domain.LocationSummary;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.LocationTransformer;
import org.apache.brooklyn.rest.transform.LocationTransformer.LocationDetailLevel;
import org.apache.brooklyn.rest.util.EntityLocationUtils;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.NaturalOrderComparator;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@SuppressWarnings("deprecation")
@HaHotStateRequired
public class LocationResource extends AbstractBrooklynRestResource implements LocationApi {

    private static final Logger log = LoggerFactory.getLogger(LocationResource.class);

    private final Set<String> specsWarnedOnException = Sets.newConcurrentHashSet();

    @Override
    public List<LocationSummary> list() {
        Function<LocationDefinition, LocationSummary> transformer = new Function<LocationDefinition, LocationSummary>() {
            @Override
            public LocationSummary apply(LocationDefinition l) {
                try {
                    return LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    String spec = l.getSpec();
                    if (spec == null || specsWarnedOnException.add(spec)) {
                        log.warn("Unable to find details of location {} in REST call to list (ignoring location): {}", l, e);
                        if (log.isDebugEnabled()) log.debug("Error details for location " + l, e);
                    } else {
                        if (log.isTraceEnabled())
                            log.trace("Unable again to find details of location {} in REST call to list (ignoring location): {}", l, e);
                    }
                    return null;
                }
            }
        };
        return FluentIterable.from(brooklyn().getLocationRegistry().getDefinedLocations().values())
                .transform(transformer)
                .filter(LocationSummary.class)
                .toSortedList(nameOrSpecComparator());
    }

    private static NaturalOrderComparator COMPARATOR = new NaturalOrderComparator();
    private static Comparator<LocationSummary> nameOrSpecComparator() {
        return new Comparator<LocationSummary>() {
            @Override
            public int compare(LocationSummary o1, LocationSummary o2) {
                return COMPARATOR.compare(getNameOrSpec(o1).toLowerCase(), getNameOrSpec(o2).toLowerCase());
            }
        };
    }
    private static String getNameOrSpec(LocationSummary o) {
        if (Strings.isNonBlank(o.getName())) return o.getName();
        if (Strings.isNonBlank(o.getSpec())) return o.getSpec();
        return o.getId();
    }

    // this is here to support the web GUI's circles
    @Override
    public Map<String,Map<String,Object>> getLocatedLocations() {
      Map<String,Map<String,Object>> result = new LinkedHashMap<String,Map<String,Object>>();
      Map<Location, Integer> counts = new EntityLocationUtils(mgmt()).countLeafEntitiesByLocatedLocations();
      for (Map.Entry<Location,Integer> count: counts.entrySet()) {
          Location l = count.getKey();
          Map<String,Object> m = MutableMap.<String,Object>of(
                  "id", l.getId(),
                  "name", l.getDisplayName(),
                  "leafEntityCount", count.getValue(),
                  "latitude", l.getConfig(LocationConfigKeys.LATITUDE),
                  "longitude", l.getConfig(LocationConfigKeys.LONGITUDE)
              );
          result.put(l.getId(), m);
      }
      return result;
    }

    /** @deprecated since 0.7.0; REST call now handled by below (optional query parameter added) */
    public LocationSummary get(String locationId) {
        return get(locationId, false);
    }

    @Override
    public LocationSummary get(String locationId, String fullConfig) {
        return get(locationId, Boolean.valueOf(fullConfig));
    }

    public LocationSummary get(String locationId, boolean fullConfig) {
        LocationDetailLevel configLevel = fullConfig ? LocationDetailLevel.FULL_EXCLUDING_SECRET : LocationDetailLevel.LOCAL_EXCLUDING_SECRET;
        Location l1 = mgmt().getLocationManager().getLocation(locationId);
        if (l1!=null) {
            return LocationTransformer.newInstance(mgmt(), l1, configLevel);
        }

        LocationDefinition l2 = brooklyn().getLocationRegistry().getDefinedLocationById(locationId);
        if (l2==null) throw WebResourceUtils.notFound("No location matching %s", locationId);
        return LocationTransformer.newInstance(mgmt(), l2, configLevel);
    }

    @Override
    public Response create(LocationSpec locationSpec) {
        String name = locationSpec.getName();
        ImmutableList.Builder<String> yaml = ImmutableList.<String>builder().add(
                "brooklyn.catalog:",
                "  symbolicName: "+name,
                "",
                "brooklyn.locations:",
                "- type: "+locationSpec.getSpec());
          if (locationSpec.getConfig().size() > 0) {
              yaml.add("  brooklyn.config:");
              for (Map.Entry<String, ?> entry : locationSpec.getConfig().entrySet()) {
                  yaml.add("    "+entry.getKey()+": "+entry.getValue());
              }
          }

          brooklyn().getCatalog().addItems(Joiner.on("\n").join(yaml.build()));
          LocationDefinition l = brooklyn().getLocationRegistry().getDefinedLocationByName(name);
          return Response.created(URI.create(name))
                  .entity(LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET))
                  .build();
    }

    @Override
    @Deprecated
    public void delete(String locationId) {
        // TODO make all locations be part of the catalog, then flip the JS GUI to use catalog api
        if (deleteAllVersions(locationId)>0) return;
        throw WebResourceUtils.notFound("No catalog item location matching %s; only catalog item locations can be deleted", locationId);
    }
    
    private int deleteAllVersions(String locationId) {
        CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(mgmt(), locationId);
        if (item==null) return 0; 
        brooklyn().getCatalog().deleteCatalogItem(item.getSymbolicName(), item.getVersion());
        return 1 + deleteAllVersions(locationId);
    }
}
