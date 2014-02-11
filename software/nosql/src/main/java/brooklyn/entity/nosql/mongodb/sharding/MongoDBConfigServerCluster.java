package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MongoDBConfigServerClusterImpl.class)
public interface MongoDBConfigServerCluster extends DynamicCluster {

    @SuppressWarnings("serial")
    AttributeSensor<Iterable<String>> SERVER_ADDRESSES = new BasicAttributeSensor<Iterable<String>>(new TypeToken<Iterable<String>>() {}, 
            "mongodb.config.server.addresses", "List of config server hostnames and ports");
    
}
