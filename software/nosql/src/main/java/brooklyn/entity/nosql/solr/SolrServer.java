/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import java.util.Map;

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

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/**
 * An {@link brooklyn.entity.Entity} that represents a Solr node.
 */
@Catalog(name="Apache Solr Node", description="Solr is the popular, blazing fast open source enterprise search " +
        "platform from the Apache Lucene project.", iconUrl="classpath:///solr-logo.jpeg")
@ImplementedBy(SolrServerImpl.class)
public interface SolrServer extends SoftwareProcess, UsesJmx, UsesJavaMXBeans {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "4.6.0");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/solr-${version}.tgz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    ConfigKey<String> MIRROR_URL = ConfigKeys.newStringConfigKey("solr.install.mirror.url", "URL of mirror",
            "http://mirrors.ukfast.co.uk/sites/ftp.apache.org/lucene/solr/");

    @SetFromFlag("tgzUrl")
    ConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "solr.install.tgzUrl", "URL of TGZ download file");

    @SetFromFlag("solrPort")
    PortAttributeSensorAndConfigKey SOLR_PORT = new PortAttributeSensorAndConfigKey("solr.http.port", "Solr HTTP communications port",
            PortRanges.fromString("8983+"));

    @SetFromFlag("solrConfigTemplateUrl")
    ConfigKey<String> SOLR_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "solr.config.templateUrl", "Template file (in freemarker format) for the solr.xml config file", 
            "classpath://brooklyn/entity/nosql/solr/solr.xml");

    @SetFromFlag("coreConfigMap")
    ConfigKey<Map<String, String>> SOLR_CORE_CONFIG = ConfigKeys.newConfigKey(new TypeToken<Map<String, String>>() { },
            "solr.core.config", "Map of core names to core configuration archive URL",
            Maps.<String, String>newHashMap());

    /* Metrics for read/write performance. */
    
    AttributeSensor<Boolean> SERVICE_UP_JMX = Sensors.newBooleanSensor("solr.service.jmx.up", "Whether JMX is up for this service");

    AttributeSensor<Double> READS_PER_SECOND_LAST = Sensors.newDoubleSensor("solr.reads.perSec.last", "Reads/sec (last datapoint)");
    AttributeSensor<Double> WRITES_PER_SECOND_LAST = Sensors.newDoubleSensor("solr.write.perSec.last", "Writes/sec (last datapoint)");

    AttributeSensor<Double> READS_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("solr.reads.perSec.windowed", "Reads/sec (over time window)");
    AttributeSensor<Double> WRITES_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("solr.writes.perSec.windowed", "Writes/sec (over time window)");

    ConfigKey<Integer> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, 3*60);
    
    /* Accessors used from template */
    
    Integer getSolrPort();

}
