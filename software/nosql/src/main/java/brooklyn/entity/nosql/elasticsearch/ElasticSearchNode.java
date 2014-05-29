package brooklyn.entity.nosql.elasticsearch;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents an ElasticSearch node
 */
@ImplementedBy(ElasticSearchNodeImpl.class)
public interface ElasticSearchNode extends SoftwareProcess, DatastoreMixins.HasDatastoreUrl {
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.0");
    
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${version}.tar.gz");
    
    @SetFromFlag("httpPort")
    PortAttributeSensorAndConfigKey HTTP_PORT = new PortAttributeSensorAndConfigKey(WebAppServiceConstants.HTTP_PORT, PortRanges.fromString("9200+"));
    
    AttributeSensor<String> NODE_ID = Sensors.newStringSensor("elasticsearch.node.id");
    AttributeSensor<String> NODE_NAME = Sensors.newStringSensor("elasticsearch.node.name");
    AttributeSensor<String> CLUSTER_NAME = Sensors.newStringSensor("elasticsearch.cluster.name");
}
