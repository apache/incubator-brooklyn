/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface SolrServerDriver extends JavaSoftwareProcessDriver {

    Integer getSolrPort();

    String getSolrConfigTemplateUrl();

}
