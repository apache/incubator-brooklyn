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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class CouchDBNodeEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        log.info("Testing Cassandra on {}", loc);

        CouchDBNode couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdb = new JcouchdbSupport(couchdb);
        jcouchdb.jcouchdbTest();
    }
}
