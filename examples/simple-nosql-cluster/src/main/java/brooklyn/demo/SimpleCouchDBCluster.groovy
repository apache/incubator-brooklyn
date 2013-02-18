package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.couchdb.CouchDBCluster;
import brooklyn.entity.proxying.BasicEntitySpec;

/** CouchDB cluster. */
public class SimpleCouchDBCluster extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(CouchDBCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "Brooklyn")
                .configure("httpPort", "8000+"));
    }

}
