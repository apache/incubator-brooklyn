/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
    
    public SolrJSupport(SolrServer node, String core) {
        this(node.getAttribute(Attributes.HOSTNAME), node.getSolrPort(), core);
    }
    
    public SolrJSupport(String hostname, int solrPort, String core) {
        server = new HttpSolrServer(String.format("http://%s:%d/solr/%s", hostname, solrPort, core));
        server.setMaxRetries(1);
        server.setConnectionTimeout(5000);
        server.setSoTimeout(5000);
    }

    public void commit() throws Exception {
        server.commit();
    }

    public void addDocument(Map<String, Object> fields) throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        for (String field : fields.keySet()) {
            doc.setField(field, fields.get(field));
        }
        server.add(doc, 100);
    }

    public Iterable<SolrDocument> getDocuments() throws Exception {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("*:*");
        
        return server.query(solrQuery).getResults();
    }
}
