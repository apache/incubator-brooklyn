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
package org.apache.brooklyn.rest;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;

import org.apache.brooklyn.rest.apidoc.RestApiResourceScanner;
import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.swagger.config.ScannerFactory;

public class RestApiSetup {

    public static ContextHandler installRestServlet(ServletContextHandler context, Object... providers) {
        ScannerFactory.setScanner(new RestApiResourceScanner());

        BrooklynRestApp app = new BrooklynRestApp();
        for (Object o : providers) {
            app.singleton(o);
        }

        CXFNonSpringJaxrsServlet servlet = new CXFNonSpringJaxrsServlet(app);
        final ServletHolder servletHolder = new ServletHolder(servlet);

        context.addServlet(servletHolder, "/v1/*");
        return context;
    }

    @SafeVarargs
    public static void installServletFilters(ServletContextHandler context, Class<? extends Filter>... filters) {
        installServletFilters(context, Arrays.asList(filters));
    }

    public static void installServletFilters(ServletContextHandler context, Collection<Class<? extends Filter>> filters) {
        for (Class<? extends Filter> filter : filters) {
            context.addFilter(filter, "/*", EnumSet.allOf(DispatcherType.class));
        }
    }

}
