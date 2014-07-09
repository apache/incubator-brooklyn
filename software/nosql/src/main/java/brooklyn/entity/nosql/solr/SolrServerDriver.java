/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface SolrServerDriver extends SoftwareProcessDriver {

    Integer getSolrPort();

    String getSolrConfigTemplateUrl();

}
