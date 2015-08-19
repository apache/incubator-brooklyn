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
package org.apache.brooklyn.camp.brooklyn.catalog;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.osgi.OsgiTestResources;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.ReaderInputStream;
import org.apache.brooklyn.util.stream.Streams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;

import com.google.common.io.ByteStreams;

public class AbstractCatalogXmlTest extends AbstractYamlTest {
    
    private String catalogUrl;
    
    public AbstractCatalogXmlTest(String catalogUrl) {
        this.catalogUrl = catalogUrl;
    }
    
    @Override
    protected LocalManagementContext newTestManagementContext() {
        ResourceUtils ru = new ResourceUtils(this);
        File jar = createJar(ru);
        File catalog = createCatalog(ru, jar);

        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, catalog.toURI().toString());
        return LocalManagementContextForTests.builder(true)
                .useProperties(properties)
                .disableOsgi(false)
                .build();
    }

    protected Entity startApp(String type) throws Exception {
        String yaml = "name: simple-app-yaml\n" +
                "location: localhost\n" +
                "services: \n" +
                "  - type: " + type;
        return createAndStartApplication(yaml);
    }

    private File createCatalog(ResourceUtils ru, File tmpJar) {
        String catalogTemplate = ru.getResourceAsString(catalogUrl);
        String catalog = catalogTemplate.replace("${osgi-entities-path}", tmpJar.toURI().toString());
        File catalogTmp = Os.newTempFile("simple-catalog-", ".xml");
        copy(catalog, catalogTmp);
        return catalogTmp;
    }

    private File createJar(ResourceUtils ru) {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        File tmpJar = Os.newTempFile("osgi-entities-", ".jar");
        InputStream in = ru.getResourceFromUrl("classpath://" + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        copy(in, tmpJar);
        return tmpJar;
    }

    private void copy(String src, File dst) {
        try {
            copy(new ReaderInputStream(new StringReader(src), "UTF-8"), dst);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private void copy(InputStream in, File tmpJar) {
        try {
            OutputStream out = new FileOutputStream(tmpJar);
            ByteStreams.copy(in, out);
            Streams.closeQuietly(in);
            Streams.closeQuietly(out);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

}
