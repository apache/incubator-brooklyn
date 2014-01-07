/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import brooklyn.entity.basic.Attributes;

/**
 * Solr testing using SolrJ API.
 */
public class SolrJSupport {

    private final HttpSolrServer server;
    
    public SolrJSupport(SolrServer node) {
        this(node.getAttribute(Attributes.HOSTNAME), node.getSolrPort());
    }
    
    public SolrJSupport(String hostname, int solrPort) {
        server = new HttpSolrServer(String.format("http://%s:%d/solr/", hostname, solrPort));
        server.setMaxRetries(1);
        server.setConnectionTimeout(5000);
    }

    public void addDocument(Map<String, Object> fields) throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        for (String field : fields.keySet()) {
            doc.setField(field, fields.get(field));
        }
        server.add(doc, 1);
    }

    public Iterable<SolrDocument> getDocuments() throws Exception {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("*:*");
        
        return server.query(solrQuery).getResults();
    }
}
