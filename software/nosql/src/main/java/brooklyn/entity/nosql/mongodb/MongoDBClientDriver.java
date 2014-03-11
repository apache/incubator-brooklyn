package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface MongoDBClientDriver extends SoftwareProcessDriver {
    void runScript(String scriptName);
}
