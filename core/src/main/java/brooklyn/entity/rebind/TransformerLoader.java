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
package brooklyn.entity.rebind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ServiceLoader;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import brooklyn.entity.rebind.transformer.RawDataTransformer;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

import com.google.common.collect.Iterables;

public class TransformerLoader {
    private ManagementContextInternal managementContext;

    public TransformerLoader(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    public Collection<RawDataTransformer> findGlobalTransformers() {
        Collection<RawDataTransformer> allTransformers = new ArrayList<RawDataTransformer>();
        Iterables.addAll(allTransformers, ServiceLoader.load(RawDataTransformer.class, managementContext.getCatalog().getRootClassLoader()));
        Maybe<OsgiManager> osgiManager = managementContext.getOsgiManager();
        if (osgiManager.isPresent()) {
            Bundle[] bundles = osgiManager.get().getFramework().getBundleContext().getBundles();
            for (Bundle bundle : bundles) {
                BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
                if (bundleWiring == null) continue; //bundle not resolved
                ClassLoader bundleClassLoader = bundleWiring.getClassLoader();
                try {
                    ServiceLoader<RawDataTransformer> bundleTransformers = ServiceLoader.load(RawDataTransformer.class, bundleClassLoader);
                    Iterables.addAll(allTransformers, bundleTransformers);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    //LOG.debug(e);
                }
            }
        }
        return allTransformers;
    }

}
