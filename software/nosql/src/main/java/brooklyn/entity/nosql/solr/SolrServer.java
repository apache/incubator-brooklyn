/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.solr;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/**
 * An {@link brooklyn.entity.Entity} that represents a Solr node.
 */
@Catalog(name="Apache Solr Node", description="Solr is the popular, blazing fast open source enterprise search " +
        "platform from the Apache Lucene project.", iconUrl="classpath:///solr-logo.jpeg")
@ImplementedBy(SolrServerImpl.class)
public interface SolrServer extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "4.7.0");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/solr-${version}.tgz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    ConfigKey<String> MIRROR_URL = ConfigKeys.newStringConfigKey("solr.install.mirror.url", "URL of mirror",
            "http://mirrors.ukfast.co.uk/sites/ftp.apache.org/lucene/solr/");

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

    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, Duration.FIVE_MINUTES);

    /* Accessors used from template */

    Integer getSolrPort();

}
