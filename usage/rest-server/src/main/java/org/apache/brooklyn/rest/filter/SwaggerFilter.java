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
package org.apache.brooklyn.rest.filter;

import io.swagger.config.ScannerFactory;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.apache.brooklyn.rest.apidoc.RestApiResourceScanner;

/**
 * Bootstraps swagger.
 *
 * Swagger was intended to run as a servlet.
 *
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class SwaggerFilter implements Filter {

    static Info info = new Info()
            .title("Brooklyn ApiDoc")
            .version("TODO") // API version, not BROOKLYN_VERSION
            //            .description("This is a sample server Petstore server.  You can find out more about Swagger " +
            //              "at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, " +
            //              "you can use the api key `special-key` to test the authorization filters.")
            //            .termsOfService("http://swagger.io/terms/")
            //            .contact(new Contact()
            //              .email("apiteam@swagger.io"))
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
        Swagger swagger = new Swagger().info(info);
//        swagger.externalDocs(new ExternalDocs("Find out more about Swagger", "http://swagger.io"));
//        swagger.securityDefinition("api_key", new ApiKeyAuthDefinition("api_key", In.HEADER));
//        swagger.securityDefinition("petstore_auth",
//                new OAuth2Definition()
//                .implicit("http://petstore.swagger.io/api/oauth/dialog")
//                .scope("read:pets", "read your pets")
//                .scope("write:pets", "modify pets in your account"));
//        swagger.tag(new Tag()
//                .name("pet")
//                .description("Everything about your Pets")
//                .externalDocs(new ExternalDocs("Find out more", "http://swagger.io")));
//        swagger.tag(new Tag()
//                .name("store")
//                .description("Access to Petstore orders"));
//        swagger.tag(new Tag()
//                .name("user")
//                .description("Operations about user")
//                .externalDocs(new ExternalDocs("Find out more about our store", "http://swagger.io")));

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
