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
package brooklyn.entity.drivers.downloads;

import java.util.Map;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Retrieves the DOWNLOAD_URL or DOWNLOAD_ADDON_URLS attribute of a given entity, and performs the
 * template substitutions to generate the download URL.
 * 
 * @author aled
 */
public class DownloadProducerFromUrlAttribute extends DownloadSubstituters.Substituter implements Function<DownloadRequirement, DownloadTargets> {
    public DownloadProducerFromUrlAttribute() {
        super(
            new Function<DownloadRequirement, String>() {
                @Override public String apply(DownloadRequirement input) {
                    if (input.getAddonName() == null) {
                        return input.getEntityDriver().getEntity().getAttribute(Attributes.DOWNLOAD_URL);
                    } else {
                        String addon = input.getAddonName();
                        Map<String, String> addonUrls = input.getEntityDriver().getEntity().getAttribute(Attributes.DOWNLOAD_ADDON_URLS);
                        return (addonUrls != null) ? addonUrls.get(addon) : null;
                    }
                }
            },
            new Function<DownloadRequirement, Map<String,?>>() {
                @Override public Map<String,?> apply(DownloadRequirement input) {
                    Map<String,Object> result = Maps.newLinkedHashMap();
                    if (input.getAddonName() == null) {
                        result.putAll(DownloadSubstituters.getBasicEntitySubstitutions(input.getEntityDriver()));
                    } else {
                        result.putAll(DownloadSubstituters.getBasicAddonSubstitutions(input.getEntityDriver(), input.getAddonName()));
                    }
                    result.putAll(input.getProperties());
                    return result;
                }
            });
    }
}
