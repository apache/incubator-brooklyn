package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface CouchbaseLoadGeneratorDriver extends SoftwareProcessDriver {

    void pillowfight(String targetHostnameAndPort, String bucket, String username, String password, Integer iterations,
        Integer numItems, String keyPrefix, Integer numThreads, Integer numInstances, Integer randomSeed, Integer ratio,
        Integer minSize, Integer maxSize);
}
