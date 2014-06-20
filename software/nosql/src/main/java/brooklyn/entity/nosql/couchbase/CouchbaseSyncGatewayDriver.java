package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface CouchbaseSyncGatewayDriver extends SoftwareProcessDriver {

    public String getOsTag();
    
}