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
package brooklyn.location.jclouds;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigUtils;
import brooklyn.location.basic.DeprecatedKeysMappingBuilder;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationPropertiesFromBrooklynProperties;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.javalang.JavaClassNames;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * <p>
 * The properties to use for a jclouds location, loaded from brooklyn.properties file
 * </p>
 * 
 * Preferred format is:
 * 
 * <ul>
 * <li>
 * brooklyn.location.named.NAME.key 
 * </li>
 * <li>
 * brooklyn.location.jclouds.PROVIDER.key
 * </li>
 * </ul>
 * 
 * <p>
 * A number of properties are also supported, listed in the {@code JcloudsLocationConfig}
 * </p>
 * 
 * @author andrea
 **/
public class JcloudsPropertiesFromBrooklynProperties extends LocationPropertiesFromBrooklynProperties {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsPropertiesFromBrooklynProperties.class);

    @SuppressWarnings("deprecation")
    private static final Map<String, String> DEPRECATED_JCLOUDS_KEYS_MAPPING = new DeprecatedKeysMappingBuilder(LOG)
            .putAll(LocationPropertiesFromBrooklynProperties.DEPRECATED_KEYS_MAPPING)
            .camelToHyphen(JcloudsLocation.IMAGE_ID)
            .camelToHyphen(JcloudsLocation.IMAGE_NAME_REGEX)
            .camelToHyphen(JcloudsLocation.IMAGE_DESCRIPTION_REGEX)
            .camelToHyphen(JcloudsLocation.HARDWARE_ID)
            .build();

    @Override
    public Map<String, Object> getLocationProperties(String provider, String namedLocation, Map<String, ?> properties) {
        throw new UnsupportedOperationException("Instead use getJcloudsProperties(String,String,String,Map)");
    }
    
    /**
     * @see LocationPropertiesFromBrooklynProperties#getLocationProperties(String, String, Map)
     */
    public Map<String, Object> getJcloudsProperties(String providerOrApi, String regionOrEndpoint, String namedLocation, Map<String, ?> properties) {
        if(Strings.isNullOrEmpty(namedLocation) && Strings.isNullOrEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }

        ConfigBag jcloudsProperties = ConfigBag.newInstance();
        String provider = getProviderName(providerOrApi, namedLocation, properties);
        
        // named properties are preferred over providerOrApi properties
        jcloudsProperties.put(LocationConfigKeys.CLOUD_PROVIDER, provider);
        jcloudsProperties.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        jcloudsProperties.putAll(transformDeprecated(getGenericJcloudsSingleWordProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getProviderOrApiJcloudsProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getRegionJcloudsProperties(providerOrApi, regionOrEndpoint, properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) jcloudsProperties.putAll(transformDeprecated(getNamedJcloudsProperties(namedLocation, properties)));
        setLocalTempDir(properties, jcloudsProperties);

        return jcloudsProperties.getAllConfigRaw();
    }

    protected String getProviderName(String providerOrApi, String namedLocationName, Map<String, ?> properties) {
        String provider = providerOrApi;
        if (!Strings.isNullOrEmpty(namedLocationName)) {
            String providerDefinition = (String) properties.get(String.format("brooklyn.location.named.%s", namedLocationName));
            if (providerDefinition!=null) {
                String provider2 = getProviderFromDefinition(providerDefinition);
                if (provider==null) {
                    // 0.7.0 25 Feb -- is this even needed?
                    LOG.warn(JavaClassNames.niceClassAndMethod()+" NOT set with provider, inferring from locationName "+namedLocationName+" as "+provider2);
                    provider = provider2;
                } else if (!provider.equals(provider2)) {
                    // 0.7.0 25 Feb -- previously we switched to provider2 in this case, but that was wrong when
                    // working with chains of names; not sure why this case would ever occur (apart from tests which have been changed)
                    // 28 Mar seen this warning many times but only in cases when NOT changing is the right behaviour 
                    LOG.debug(JavaClassNames.niceClassAndMethod()+" NOT changing provider from "+provider+" to candidate "+provider2);
                }
            }
        }
        return provider;
    }
    
    protected String getProviderFromDefinition(String definition) {
        return Iterables.get(Splitter.on(":").split(definition), 1);
    }

    protected Map<String, Object> getGenericJcloudsSingleWordProperties(String providerOrApi, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi)) return Maps.newHashMap();
        String deprecatedPrefix = "brooklyn.jclouds.";
        String preferredPrefix = "brooklyn.location.jclouds.";
        return getMatchingSingleWordProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getProviderOrApiJcloudsProperties(String providerOrApi, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi)) return Maps.newHashMap();
        String preferredPrefix = String.format("brooklyn.location.jclouds.%s.", providerOrApi);
        String deprecatedPrefix = String.format("brooklyn.jclouds.%s.", providerOrApi);
        
        return getMatchingProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getRegionJcloudsProperties(String providerOrApi, String regionName, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi) || Strings.isNullOrEmpty(regionName)) return Maps.newHashMap();
        String preferredPrefix = String.format("brooklyn.location.jclouds.%s@%s.", providerOrApi, regionName);
        String deprecatedPrefix = String.format("brooklyn.jclouds.%s@%s.", providerOrApi, regionName);
        
        return getMatchingProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getNamedJcloudsProperties(String locationName, Map<String, ?> properties) {
        if(locationName == null) return Maps.newHashMap();
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    @Override
    protected Map<String, String> getDeprecatedKeysMapping() {
        return DEPRECATED_JCLOUDS_KEYS_MAPPING;
    }
}
