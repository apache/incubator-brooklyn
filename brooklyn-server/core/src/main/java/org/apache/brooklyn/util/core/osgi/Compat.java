/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.brooklyn.util.core.osgi;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Compatibility methods between karaf launcher and monolithic launcher.
 *
 * @todo Remove after transition to karaf launcher.
 */
public class Compat {

    /* synchronized by class initialization */
    private static class SingletonHolder {

        private static final Compat instance = new Compat();
    }

    public static Compat getInstance() {
        return SingletonHolder.instance;
    }

    private final ServiceTracker managementContextTracker;

    private Compat() {
        Bundle bundle = FrameworkUtil.getBundle(Compat.class);
        if (bundle != null) {
            BundleContext bundleContext = bundle.getBundleContext();
            managementContextTracker = new ServiceTracker(bundleContext, ManagementContext.class, null);
            managementContextTracker.open();
        } else {
            managementContextTracker = null;
        }
    }

    /**
     * Provides the management context service.
     *
     * Either from the encompassing OSGi framework or from the servlet context, depending on which launcher was used.
     *
     * @todo This does not allow ungetting the service after usage, so the bundle will remain blocked until all dependent bundles are
     * stopped.
     * @fixme Drop this for good after switching to karaf launcher.
     */
    public ManagementContext getManagementContext() {
        if (managementContextTracker != null) {
            return (ManagementContext) managementContextTracker.getService();
        }
        return null;
    }
}
