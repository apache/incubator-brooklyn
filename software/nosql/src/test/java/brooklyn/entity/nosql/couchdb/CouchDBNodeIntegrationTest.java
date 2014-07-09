/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.nosql.couchdb;

import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * CouchDB integration tests.
 *
 * Test the operation of the {@link CouchDBNode} class.
 */
public class CouchDBNodeIntegrationTest extends AbstractCouchDBNodeTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = {"Integration", "WIP"})
    public void canStartupAndShutdown() {
        couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        couchdb.stop();

        EntityTestUtils.assertAttributeEquals(couchdb, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a node can be used with jcouchdb client.
     */
    @Test(groups = {"Integration", "WIP"})
    public void testConnection() throws Exception {
        couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdb = new JcouchdbSupport(couchdb);
        jcouchdb.jcouchdbTest();
    }
}
