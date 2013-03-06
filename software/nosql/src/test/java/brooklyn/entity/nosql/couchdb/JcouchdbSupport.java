/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jcouchdb.db.Database;
import org.jcouchdb.db.Server;
import org.jcouchdb.db.ServerImpl;

import brooklyn.entity.basic.Attributes;

/**
 * CouchDB test framework for integration and live tests, using jcouchdb API.
 */
public class JcouchdbSupport {

    private CouchDBNode node;

    public JcouchdbSupport(CouchDBNode node) {
        this.node = node;
    }

    /**
     * Exercise the {@link CouchDBNode} using the jcouchdb API.
     */
    public void jcouchdbTest() throws Exception {
        Server server = new ServerImpl(node.getAttribute(Attributes.HOSTNAME), node.getHttpPort());
        assertTrue(server.createDatabase("brooklyn"));

        Database db = new Database(node.getAttribute(Attributes.HOSTNAME), node.getHttpPort(), "brooklyn");

        // create a hash map document with two fields
        Map<String,String> doc = new HashMap<String, String>();
        doc.put("first", "one");
        doc.put("second", "two");

        // create the document in couchdb
        int before = db.listDocuments(null, null).getTotalRows();
        db.createDocument(doc);
        int after = db.listDocuments(null, null).getTotalRows();

        assertEquals(before + 1, after);
    }

    /**
     * Write to a {@link CouchDBNode} using the jcouchdb API.
     */
    protected void writeData() throws Exception {
    }

    /**
     * Read from a {@link CouchDBNode} using the jcouchdb API.
     */
    protected void readData() throws Exception {
    }
}
