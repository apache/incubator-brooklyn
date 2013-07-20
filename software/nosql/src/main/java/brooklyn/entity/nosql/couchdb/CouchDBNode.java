/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.nosql.couchdb;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a CouchDB node in a {@link CouchDBCluster}.
 */
@Catalog(name="CouchDB Node", description="Apache CouchDB is a database that uses JSON for documents, JavaScript for MapReduce queries, and regular HTTP for an API", iconUrl="classpath:///couchdb-logo.png")
@ImplementedBy(CouchDBNodeImpl.class)
public interface CouchDBNode extends SoftwareProcess, WebAppService {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.1");

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = CouchDBCluster.CLUSTER_NAME;

    @SetFromFlag("couchdbConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.config.templateUrl", "Template file (in freemarker format) for the couchdb config file", 
            "classpath://brooklyn/entity/nosql/couchdb/couch.ini");

    @SetFromFlag("couchdbUriTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_URI_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.uri.templateUrl", "Template file (in freemarker format) for the couchdb URI file", 
            "classpath://brooklyn/entity/nosql/couchdb/couch.uri");

    @SetFromFlag("couchdbConfigFileName")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.config.fileName", "Name for the copied config file", "local.ini");

    Integer getHttpPort();

    Integer getHttpsPort();
}
