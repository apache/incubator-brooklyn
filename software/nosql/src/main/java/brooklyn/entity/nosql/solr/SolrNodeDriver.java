/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface SolrNodeDriver extends JavaSoftwareProcessDriver {

    Integer getSolrPort();

    String getSolrConfigTemplateUrl();

    String getSolrConfigFileName();

}
