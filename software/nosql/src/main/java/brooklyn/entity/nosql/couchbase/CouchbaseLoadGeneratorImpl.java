package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class CouchbaseLoadGeneratorImpl extends SoftwareProcessImpl implements CouchbaseLoadGenerator {

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return CouchbaseLoadGeneratorSshDriver.class;
    }
    
    @Override
    public CouchbaseLoadGeneratorSshDriver getDriver() {
        return (CouchbaseLoadGeneratorSshDriver)super.getDriver();
    }

    @Override
    public void pillowfight(String targetHostnameAndPort, String bucket, String username, String password, Integer iterations,
            Integer numItems, String keyPrefix, Integer numThreads, Integer numInstances, Integer randomSeed, Integer ratio,
            Integer minSize, Integer maxSize) {
        getDriver().pillowfight(targetHostnameAndPort, bucket, username, password, 
                iterations, numItems, keyPrefix, numThreads, numInstances, randomSeed, ratio, minSize, maxSize);
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

}
