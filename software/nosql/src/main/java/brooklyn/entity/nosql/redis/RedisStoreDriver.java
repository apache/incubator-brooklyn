package brooklyn.entity.nosql.redis;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface RedisStoreDriver extends SoftwareProcessDriver {
    
    String getRunDir();
}
