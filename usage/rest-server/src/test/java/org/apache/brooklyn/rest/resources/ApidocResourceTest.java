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
package org.apache.brooklyn.rest.resources;


import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.core.ClassNamesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.apache.brooklyn.rest.BrooklynRestApi;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainerException;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory;
import io.swagger.annotations.Api;
import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import java.util.Collection;
import org.apache.brooklyn.rest.api.CatalogApi;
import org.apache.brooklyn.rest.api.EffectorApi;
import org.apache.brooklyn.rest.api.EntityApi;
import org.apache.brooklyn.rest.filter.SwaggerFilter;
import org.apache.brooklyn.rest.util.ShutdownHandlerProvider;
import org.python.google.common.base.Joiner;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * @author Adam Lowe
 */
@Test(singleThreaded = true)
public class ApidocResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ApidocResourceTest.class);

    @Override
    protected JerseyTest createJerseyTest() {
        return new JerseyTest() {
            @Override
            protected AppDescriptor configure() {
                return new WebAppDescriptor.Builder(
                        ImmutableMap.of(
                                ServletContainer.RESOURCE_CONFIG_CLASS, ClassNamesResourceConfig.class.getName(),
                                ClassNamesResourceConfig.PROPERTY_CLASSNAMES, getResourceClassnames()))
                        .addFilter(SwaggerFilter.class, "SwaggerFilter").build();
            }

            @Override
            protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
                return new GrizzlyWebTestContainerFactory();
            }

            private String getResourceClassnames() {
                Iterable<String> classnames = Collections2.transform(config.getClasses(), new Function<Class, String>() {
                    @Override
                    public String apply(Class clazz) {
                        return clazz.getName();
                    }
                });
                classnames = Iterables.concat(classnames, Collections2.transform(config.getSingletons(), new Function<Object, String>() {
                    @Override
                    public String apply(Object singleton) {
                        return singleton.getClass().getName();
                    }
                }));
                return Joiner.on(';').join(classnames);
            }
        };
    }

    @Override
    protected void addBrooklynResources() {
        for (Object o : BrooklynRestApi.getApidocResources()) {
            addResource(o);
        }
        super.addBrooklynResources();
    }
    
    @Test(enabled = false)
    public void testRootSerializesSensibly() throws Exception {
        String data = resource("/v1/apidoc/swagger.json").get(String.class);
        log.info("apidoc gives: "+data);
        // make sure no scala gets in
        assertFalse(data.contains("$"));
        assertFalse(data.contains("scala"));
        // make sure it's an appropriate swagger 2.0 json
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        assertEquals(swagger.getSwagger(), "2.0");
    }
    
    @Test(enabled = false)
    public void testCountRestResources() throws Exception {
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        assertEquals(swagger.getTags().size(), 1 + Iterables.size(BrooklynRestApi.getBrooklynRestResources()));
    }

    @Test(enabled = false)
    public void testApiDocDetails() throws Exception {
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        Collection<Operation> operations = getTaggedOperations(swagger, ApidocResource.class.getAnnotation(Api.class).value());
        assertEquals(operations.size(), 2, "ops="+operations);
    }

    @Test(enabled = false)
    public void testEffectorDetails() throws Exception {
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        Collection<Operation> operations = getTaggedOperations(swagger, EffectorApi.class.getAnnotation(Api.class).value());
        assertEquals(operations.size(), 2, "ops="+operations);
    }

    @Test(enabled = false)
    public void testEntityDetails() throws Exception {
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        Collection<Operation> operations = getTaggedOperations(swagger, EntityApi.class.getAnnotation(Api.class).value());
        assertEquals(operations.size(), 14, "ops="+operations);
    }

    @Test(enabled = false)
    public void testCatalogDetails() throws Exception {
        Swagger swagger = resource("/v1/apidoc/swagger.json").get(Swagger.class);
        Collection<Operation> operations = getTaggedOperations(swagger, CatalogApi.class.getAnnotation(Api.class).value());
        assertEquals(operations.size(), 22, "ops="+operations);
    }

    /**
     * Retrieves all operations tagged the given tag from the given swagger spec.
     */
    private Collection<Operation> getTaggedOperations(Swagger swagger, final String requiredTag) {
        Iterable<Operation> allOperations = Iterables.concat(Collections2.transform(swagger.getPaths().values(),
                new Function<Path, Collection<Operation>>() {
                    @Override
                    public Collection<Operation> apply(Path path) {
                        return path.getOperations();
                    }
                }));

        return Collections2.filter(ImmutableList.copyOf(allOperations), new Predicate<Operation>() {
            @Override
            public boolean apply(Operation operation) {
                return operation.getTags().contains(requiredTag);
            }
        });
    }
}

