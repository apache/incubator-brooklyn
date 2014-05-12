package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(CouchbaseLoadGeneratorImpl.class)
public interface CouchbaseLoadGenerator extends SoftwareProcess {

}
