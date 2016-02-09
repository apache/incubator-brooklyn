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
package org.apache.brooklyn.rest.util;

import javax.servlet.ServletContext;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.server.BrooklynServiceAttributes;
import org.apache.brooklyn.util.core.osgi.Compat;

/**
 * Compatibility methods between karaf launcher and monolithic launcher.
 *
 * @todo Remove after transition to karaf launcher.
 */
public class OsgiCompat {

    public static ManagementContext getManagementContext(ServletContext servletContext) {
        ManagementContext managementContext = Compat.getInstance().getManagementContext();
        if (managementContext == null && servletContext != null) {
            managementContext = (ManagementContext) servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        }
        return managementContext;
    }


}
