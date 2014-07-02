package brooklyn.entity.nosql.solr;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface SolrServerDriver extends SoftwareProcessDriver {

    Integer getSolrPort();

    String getSolrConfigTemplateUrl();

}
