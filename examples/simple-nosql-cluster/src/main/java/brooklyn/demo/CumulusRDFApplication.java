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
package brooklyn.demo;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;

/** CumulusRDF application with Cassandra cluster. */
public class CumulusRDFApplication extends AbstractApplication {

    /** Create entities. */
    public void init() {
        TomcatServer tomcat = getEntityManager().createEntity(EntitySpec.create(TomcatServer.class)
                .configure("war", "classpath:///cumulusrdf.war"));

        CassandraCluster cassandra = getEntityManager().createEntity(EntitySpec.create(CassandraCluster.class)
                .configure("initialSize", "4")
                .configure("clusterName", "CumulusRDF")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));

        addChild(tomcat);
        addChild(cassandra);
    }
}
