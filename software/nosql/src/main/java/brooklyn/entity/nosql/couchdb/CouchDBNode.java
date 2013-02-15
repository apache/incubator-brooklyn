/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a CouchDB node in a {@link CouchDBCluster}.
 */
@ImplementedBy(CouchDBNodeImpl.class)
public interface CouchDBNode extends SoftwareProcess, WebAppService {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "1.2.1");

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
