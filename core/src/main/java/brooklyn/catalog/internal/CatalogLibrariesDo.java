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
package brooklyn.catalog.internal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.Time;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class CatalogLibrariesDo implements CatalogItem.CatalogItemLibraries {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogLibrariesDo.class);
    private final CatalogLibrariesDto librariesDto;


    public CatalogLibrariesDo(CatalogLibrariesDto librariesDto) {
        this.librariesDto = Preconditions.checkNotNull(librariesDto, "librariesDto");
    }

    @Override
    public List<String> getBundles() {
        return librariesDto.getBundles();
    }

    /**
     * Registers all bundles with the management context's OSGi framework.
     */
    void load(ManagementContext managementContext) {
        ManagementContextInternal mgmt = (ManagementContextInternal) managementContext;
        Maybe<OsgiManager> osgi = mgmt.getOsgiManager();
        List<String> bundles = getBundles();
        if (osgi.isAbsent()) {
            LOG.warn("{} not loading bundles in {} because osgi manager is unavailable. Bundles: {}",
                    new Object[]{this, managementContext, Joiner.on(", ").join(bundles)});
            return;
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("{} loading bundles in {}: {}",
                    new Object[]{this, managementContext, Joiner.on(", ").join(bundles)});
        }
        Stopwatch timer = Stopwatch.createStarted();
        for (String bundleUrl : bundles) {
            osgi.get().registerBundle(bundleUrl);
        }
        LOG.debug("{} registered {} bundles in {}",
                new Object[]{this, bundles.size(), Time.makeTimeStringRounded(timer)});
    }

}
