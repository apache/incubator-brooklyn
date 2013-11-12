/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJavaMXBeans;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a Solr node.
 */
@Catalog(name="Apache Solr Node", description="Solr is the popular, blazing fast open source enterprise search " +
        "platform from the Apache Lucene project.", iconUrl="classpath:///solr-logo.jpeg")
@ImplementedBy(SolrNodeImpl.class)
public interface SolrNode extends SoftwareProcess, UsesJmx, UsesJavaMXBeans {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "4.5.1");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/apache-solr-${version}-bin.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    ConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "solr.install.mirror.url", "URL of mirror", "http://www.mirrorservice.org/sites/ftp.apache.org/solr");

    @SetFromFlag("tgzUrl")
    ConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "solr.install.tgzUrl", "URL of TGZ download file");

    @SetFromFlag("gossipPort")
    PortAttributeSensorAndConfigKey SOLR_PORT = new PortAttributeSensorAndConfigKey("solr.gossip.port", "Solr Gossip communications port", PortRanges.fromString("7000+"));

    @SetFromFlag("solrConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> SOLR_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "solr.config.templateUrl", "Template file (in freemarker format) for the solr.yaml config file", 
            "classpath://brooklyn/entity/nosql/solr/solr.yaml");

    @SetFromFlag("solrConfigFileName")
    BasicAttributeSensorAndConfigKey<String> SOLR_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "solr.config.fileName", "Name for the copied config file", "solr.yaml");

    /* Metrics for read/write performance. */
    
    AttributeSensor<Boolean> SERVICE_UP_JMX = Sensors.newBooleanSensor("solr.service.jmx.up", "Whether JMX is up for this service");

    AttributeSensor<Double> READS_PER_SECOND_LAST = Sensors.newDoubleSensor("solr.reads.perSec.last", "Reads/sec (last datapoint)");
    AttributeSensor<Double> WRITES_PER_SECOND_LAST = Sensors.newDoubleSensor("solr.write.perSec.last", "Writes/sec (last datapoint)");

    AttributeSensor<Double> READS_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("solr.reads.perSec.windowed", "Reads/sec (over time window)");
    AttributeSensor<Double> WRITES_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("solr.writes.perSec.windowed", "Writes/sec (over time window)");

    ConfigKey<Integer> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, 3*60);
    
    /* Accessors used from template */
    
    Integer getSolrPort();
    String getListenAddress();
    String getBroadcastAddress();
}
