package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

public class MongoDBConfigServerClusterImpl extends DynamicClusterImpl implements MongoDBConfigServerCluster {
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBConfigServer.class);
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        super.start(locs);
        Iterable<String> memberHostNamesAndPorts = Iterables.transform(getMembers(), new Function<Entity, String>() {
            @Override
            public String apply(Entity entity) {
                return entity.getAttribute(MongoDBConfigServer.HOSTNAME) + ":" + entity.getAttribute(MongoDBConfigServer.PORT);
            }
        });
        setAttribute(MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES, memberHostNamesAndPorts);
    }

}
