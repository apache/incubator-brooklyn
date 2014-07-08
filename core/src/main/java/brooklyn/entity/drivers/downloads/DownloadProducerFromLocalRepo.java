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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.base.Function;

public class DownloadProducerFromLocalRepo implements Function<DownloadRequirement, DownloadTargets> {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadProducerFromLocalRepo.class);

    public static final ConfigKey<String> LOCAL_REPO_PATH = BasicConfigKey.builder(String.class)
            .name(DownloadProducerFromProperties.DOWNLOAD_CONF_PREFIX+"repo.local.path")
            .description("Fully qualified path of the local repo")
            .defaultValue("$HOME/.brooklyn/repository")
            .build();

    public static final ConfigKey<Boolean> LOCAL_REPO_ENABLED = BasicConfigKey.builder(Boolean.class)
            .name(DownloadProducerFromProperties.DOWNLOAD_CONF_PREFIX+"repo.local.enabled")
            .description("Whether to use the local repo for downloading entities, during installs")
            .defaultValue(true)
            .build();

    public static final String LOCAL_REPO_URL_PATTERN = "file://%s/"+
            "${simpletype}/${version}/"+
            "<#if filename??>"+
                "${filename}" +
            "<#else>"+
              "<#if addon??>"+
                "${simpletype?lower_case}-${addon?lower_case}-${addonversion?lower_case}.${fileSuffix!\"tar.gz\"}"+
              "<#else>"+
                  "${simpletype?lower_case}-${version?lower_case}.${fileSuffix!\"tar.gz\"}"+
              "</#if>"+
            "</#if>";


    private final StringConfigMap config;

    public DownloadProducerFromLocalRepo(StringConfigMap config) {
        this.config = config;
    }
    
    public DownloadTargets apply(DownloadRequirement req) {
        Boolean enabled = config.getConfig(LOCAL_REPO_ENABLED);
        String path = config.getConfig(LOCAL_REPO_PATH);
        String url = String.format(LOCAL_REPO_URL_PATTERN, path);
        
        if (enabled) {
            Map<String, ?> subs = DownloadSubstituters.getBasicSubstitutions(req);
            String result = DownloadSubstituters.substitute(url, subs);
            return BasicDownloadTargets.builder().addPrimary(result).build();
            
        } else {
            return BasicDownloadTargets.empty();
        }
    }
}
