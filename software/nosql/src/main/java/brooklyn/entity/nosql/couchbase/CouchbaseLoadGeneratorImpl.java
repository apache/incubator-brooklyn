package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class CouchbaseLoadGeneratorImpl extends SoftwareProcessImpl implements CouchbaseLoadGenerator {

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return CouchbaseLoadGeneratorSshDriver.class;
    }

}
