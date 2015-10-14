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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.testng.annotations.Test;

public class CatalogXmlOsgiTest extends AbstractCatalogXmlTest {

    public CatalogXmlOsgiTest(String catalogUrl) {
        super("classpath://osgi-catalog.xml");
    }

    //OSGi libraries not supported with old-style catalog items
    //We treat those catalog items just as an alias to the java type they hold.
    //No loader wrapping their libraries is ever created.
    @Test
    public void testOsgiItem() throws Exception {
        try {
            startApp("OsgiApp");
            fail();
        } catch (PropagatedRuntimeException e) {
            ClassNotFoundException ex = Exceptions.getFirstThrowableOfType(e, ClassNotFoundException.class);
            if (ex == null) throw e;
            assertEquals(ex.getMessage(), "org.apache.brooklyn.test.osgi.entities.SimpleApplication");
        }
    }

}
