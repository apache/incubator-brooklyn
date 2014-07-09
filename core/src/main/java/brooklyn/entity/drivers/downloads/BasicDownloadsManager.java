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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BasicDownloadsManager implements DownloadResolverManager {

    private final List<Function<? super DownloadRequirement, ? extends DownloadTargets>> producers = Lists.newCopyOnWriteArrayList();

    private final List<Function<? super DownloadRequirement, String>> filenameProducers = Lists.newCopyOnWriteArrayList();

    /**
     * The default is (in-order) to:
     * <ol>
     *   <li>Use the local repo, if any (defaulting to $HOME/.brooklyn/repository)
     *   <li>Use brooklyn properties for any download overrides defined there (see {@link DownloadProducerFromProperties}
     *   <li>Use the entity's Attributes.DOWNLOAD_URL
     *   <li>Use the cloudsoft fallback repo
     * </ol>
     * @param config
     */
    public static BasicDownloadsManager newDefault(StringConfigMap config) {
        BasicDownloadsManager result = new BasicDownloadsManager();
        
        // In-order, will look up: local repo, overrides defined in the properties, and then 
        // the entity's attribute to get the download URL
        DownloadProducerFromLocalRepo localRepoProducer = new DownloadProducerFromLocalRepo(config);
        DownloadProducerFromProperties propertiesProducer = new DownloadProducerFromProperties(config);
        DownloadProducerFromUrlAttribute attributeProducer = new DownloadProducerFromUrlAttribute();
        DownloadProducerFromCloudsoftRepo cloudsoftRepoProducer = new DownloadProducerFromCloudsoftRepo(config);
        
        result.registerProducer(localRepoProducer);
        result.registerProducer(propertiesProducer);
        result.registerProducer(attributeProducer);
        result.registerProducer(cloudsoftRepoProducer);
        
        result.registerFilenameProducer(FilenameProducers.fromFilenameProperty());
        result.registerFilenameProducer(FilenameProducers.firstPrimaryTargetOf(propertiesProducer));
        result.registerFilenameProducer(FilenameProducers.firstPrimaryTargetOf(attributeProducer));
        
        return result;
    }
    
    public static BasicDownloadsManager newEmpty() {
        return new BasicDownloadsManager();
    }
    
    @Override
    public void registerPrimaryProducer(Function<? super DownloadRequirement, ? extends DownloadTargets> producer) {
        producers.add(0, checkNotNull(producer, "resolver"));
    }

    @Override
    public void registerProducer(Function<? super DownloadRequirement, ? extends DownloadTargets> producer) {
        producers.add(checkNotNull(producer, "resolver"));
    }

    @Override
    public void registerFilenameProducer(Function<? super DownloadRequirement, String> producer) {
        filenameProducers.add(checkNotNull(producer, "producer"));
    }

    @Override
    public DownloadResolver newDownloader(EntityDriver driver) {
        return newDownloader(new BasicDownloadRequirement(driver));
    }

    @Override
    public DownloadResolver newDownloader(EntityDriver driver, Map<String, ?> properties) {
        return newDownloader(new BasicDownloadRequirement(driver, properties));
    }

    @Override
    public DownloadResolver newDownloader(EntityDriver driver, String addonName, Map<String, ?> addonProperties) {
        return newDownloader(new BasicDownloadRequirement(driver, addonName, addonProperties));
    }

    private DownloadResolver newDownloader(DownloadRequirement req) {
        // Infer filename
        String filename = null;
        for (Function<? super DownloadRequirement, String> filenameProducer : filenameProducers) {
            filename = filenameProducer.apply(req);
            if (!Strings.isBlank(filename)) break;
        }
        
        // If a filename-producer has given us the filename, then augment the DownloadRequirement with that
        // (so that local-repo substitutions etc can use that explicit filename)
        DownloadRequirement wrappedReq;
        if (filename == null) {
            wrappedReq = req;
        } else {
            wrappedReq = BasicDownloadRequirement.copy(req, ImmutableMap.of("filename", filename));
        }
        
        // Get ordered download targets to be tried
        List<String> primaries = Lists.newArrayList();
        List<String> fallbacks = Lists.newArrayList();
        for (Function<? super DownloadRequirement, ? extends DownloadTargets> producer : producers) {
            DownloadTargets vals = producer.apply(wrappedReq);
            primaries.addAll(vals.getPrimaryLocations());
            fallbacks.addAll(vals.getFallbackLocations());
            if (!vals.canContinueResolving()) {
                break;
            }
        }

        Set<String> result = Sets.newLinkedHashSet();
        result.addAll(primaries);
        result.addAll(fallbacks);

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No downloads matched for "+req);
        }
        
        // If filename-producers didn't give any explicit filename, then infer from download results
        if (filename == null) {
            for (String target : result) {
                filename = FilenameProducers.inferFilename(target);
                if (!Strings.isBlank(filename)) break;
            }
        }
        if (Strings.isBlank(filename)) {
            throw new IllegalArgumentException("No filenames matched for "+req+" (targets "+result+")");
        }
        
        // And return the result
        return new BasicDownloadResolver(result, filename);
    }
}
