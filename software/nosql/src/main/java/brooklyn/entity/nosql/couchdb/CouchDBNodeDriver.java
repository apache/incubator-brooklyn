package brooklyn.entity.nosql.couchdb;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface CouchDBNodeDriver extends SoftwareProcessDriver {

    Integer getHttpPort();

    Integer getHttpsPort();

    String getClusterName();

    String getCouchDBConfigTemplateUrl();

    String getCouchDBUriTemplateUrl();

    String getCouchDBConfigFileName();

}
