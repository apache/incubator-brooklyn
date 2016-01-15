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
package org.apache.brooklyn.entity.nosql.couchdb;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a CouchDB node in a {@link CouchDBCluster}.
 */
@Catalog(name="CouchDB Node",
        description="Apache CouchDB is a database that uses JSON for documents, JavaScript for MapReduce queries, " +
                "and regular HTTP for an API",
        iconUrl="classpath:///couchdb-logo.png")
@ImplementedBy(CouchDBNodeImpl.class)
public interface CouchDBNode extends SoftwareProcess, WebAppService {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.1");

    @SetFromFlag("erlangVersion")
    ConfigKey<String> ERLANG_VERSION = ConfigKeys.newStringConfigKey("erlang.version", "Erlang runtime version", "R15B");

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = CouchDBCluster.CLUSTER_NAME;

    @SetFromFlag("couchdbConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.config.templateUrl", "Template file (in freemarker format) for the couchdb config file", 
            "classpath://org/apache/brooklyn/entity/nosql/couchdb/couch.ini");

    @SetFromFlag("couchdbUriTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_URI_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.uri.templateUrl", "Template file (in freemarker format) for the couchdb URI file", 
            "classpath://org/apache/brooklyn/entity/nosql/couchdb/couch.uri");

    @SetFromFlag("couchdbConfigFileName")
    BasicAttributeSensorAndConfigKey<String> COUCHDB_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "couchdb.config.fileName", "Name for the copied config file", "local.ini");

    Integer getHttpPort();

    Integer getHttpsPort();
}
