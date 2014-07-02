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
