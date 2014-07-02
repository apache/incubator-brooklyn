package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.couchdb.CouchDBCluster;
import brooklyn.entity.proxying.EntitySpec;

/** CouchDB cluster. */
public class SimpleCouchDBCluster extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        addChild(EntitySpec.create(CouchDBCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "Brooklyn")
                .configure("httpPort", "8000+"));
    }

}
