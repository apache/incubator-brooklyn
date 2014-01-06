/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;

/**
 * Solr testing using SolrJ API.
 */
public class SolrJSupport {
    private static final Logger log = LoggerFactory.getLogger(SolrJSupport.class);

    public final String hostname;
    public final int solrPort;
    
    public SolrJSupport(SolrServer node) {
        this(node.getAttribute(Attributes.HOSTNAME), node.getSolrPort());
    }
    
    public SolrJSupport(String hostname, int solrPort) {
        this.hostname = hostname;
        this.solrPort = solrPort;
    }
}
