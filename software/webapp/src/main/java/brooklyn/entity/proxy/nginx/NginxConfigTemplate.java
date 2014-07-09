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
package brooklyn.entity.proxy.nginx;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * Processes a FreeMarker template for an {@link NginxController} configuration file.
 */
public class NginxConfigTemplate {

    private NginxDriver driver;

    public static NginxConfigTemplate generator(NginxDriver driver) {
        return new NginxConfigTemplate(driver);
    }

    private NginxConfigTemplate(NginxDriver driver) {
        this.driver = driver;
    }

    public String configFile() {
        // Check template URL exists
        String templateUrl = driver.getEntity().getConfig(NginxController.SERVER_CONF_TEMPLATE_URL);
        ResourceUtils.create(this).checkUrlExists(templateUrl);

        // Check SSL configuration
        ProxySslConfig ssl = driver.getEntity().getConfig(NginxController.SSL_CONFIG);
        if (ssl != null && Strings.isEmpty(ssl.getCertificateDestination()) && Strings.isEmpty(ssl.getCertificateSourceUrl())) {
            throw new IllegalStateException("ProxySslConfig can't have a null certificateDestination and null certificateSourceUrl. One or both need to be set");
        }

        // For mapping by URL
        Iterable<UrlMapping> mappings = ((NginxController) driver.getEntity()).getUrlMappings();
        Multimap<String, UrlMapping> mappingsByDomain = LinkedHashMultimap.create();
        for (UrlMapping mapping : mappings) {
            Collection<String> addrs = mapping.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                mappingsByDomain.put(mapping.getDomain(), mapping);
            }
        }
        Map<String, Object> substitutions = MutableMap.<String, Object>builder()
                .putIfNotNull("ssl", ssl)
                .put("urlMappings", mappings)
                .put("domainMappings", mappingsByDomain)
                .build();

        // Get template contents and process
        String contents = ResourceUtils.create(driver.getEntity()).getResourceAsString(templateUrl);
        return TemplateProcessor.processTemplateContents(contents, driver, substitutions);
    }

}
