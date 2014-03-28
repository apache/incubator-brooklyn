package brooklyn.entity.nosql.mongodb;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface AbstractMongoDBServer extends SoftwareProcess, Entity {

    @SetFromFlag("dataDirectory")
    ConfigKey<String> DATA_DIRECTORY = ConfigKeys.newStringConfigKey(
            "mongodb.data.directory", "Data directory to store MongoDB journals");
    
    @SetFromFlag("mongodbConfTemplateUrl")
    ConfigKey<String> MONGODB_CONF_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "mongodb.config.url", "Template file (in freemarker format) for a MongoDB configuration file",
            "classpath://brooklyn/entity/nosql/mongodb/default-mongodb.conf");
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.5.4");

    // TODO: Windows support
    // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
    // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
    // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
    // Note Windows download is a zip.
    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://fastdl.mongodb.org/${driver.osDir}/${driver.osTag}-${version}.tgz");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");
}