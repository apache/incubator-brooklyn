/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface CouchDBNodeDriver extends JavaSoftwareProcessDriver {

    Integer getHttpPort();

    Integer getHttpsPort();

    String getClusterName();

    String getCouchDBConfigTemplateUrl();

    String getCouchDBUriTemplateUrl();

    String getCouchDBConfigFileName();

}
