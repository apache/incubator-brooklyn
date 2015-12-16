/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.rest.filter;

import io.swagger.config.ScannerFactory;
import io.swagger.models.ExternalDocs;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import org.apache.brooklyn.rest.apidoc.RestApiResourceScanner;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Bootstraps swagger.
 * <p>
 * Swagger was intended to run as a servlet.
 *
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class SwaggerFilter implements Filter {

    static Info info = new Info()
            .title("Brooklyn API Documentation")
            .version("v1") // API version, not BROOKLYN_VERSION
            .license(new License()
                    .name("Apache 2.0")
                    .url("http://www.apache.org/licenses/LICENSE-2.0.html"));

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
//        ReflectiveJaxrsScanner scanner = new ReflectiveJaxrsScanner();
//        scanner.setResourcePackage("org.apache.brooklyn.rest.api,org.apache.brooklyn.rest.apidoc,org.apache.brooklyn.rest.resources");
//        ScannerFactory.setScanner(scanner);
        ScannerFactory.setScanner(new RestApiResourceScanner());

        ServletContext context = filterConfig.getServletContext();
        Swagger swagger = new Swagger()
                .info(info)
                .basePath("/");
        context.setAttribute("swagger", swagger);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

}
