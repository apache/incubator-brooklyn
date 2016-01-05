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

import org.testng.annotations.Test;

public class CatalogXmlOsgiTest extends AbstractCatalogXmlTest {

    public CatalogXmlOsgiTest(String catalogUrl) {
        super("classpath://osgi-catalog.xml");
    }

    @Test
    public void testOsgiItem() throws Exception {
        startApp("OsgiApp");
        // previously OSGi libraries were not supported with old-style catalog items;
        // now they are (2015-10), even though the XML is not supported,
        // we may use the same type instantiator from CAMP and elsewhere
    }

}
