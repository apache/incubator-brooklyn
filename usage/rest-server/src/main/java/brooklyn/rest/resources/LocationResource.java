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
package brooklyn.rest.resources;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.rest.api.LocationApi;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.domain.SummaryComparators;
import brooklyn.rest.transform.LocationTransformer;
import brooklyn.rest.transform.LocationTransformer.LocationDetailLevel;
import brooklyn.rest.util.EntityLocationUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

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
                .toSortedList(SummaryComparators.nameComparator());
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
      String id = Identifiers.makeRandomId(8);
      LocationDefinition l = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
      brooklyn().getLocationRegistry().updateDefinedLocation(l);
      return Response.created(URI.create(id))
              .entity(LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET))
              .build();
  }

  public void delete(String locationId) {
      brooklyn().getLocationRegistry().removeDefinedLocation(locationId);
  }

}
