/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface CassandraNodeDriver extends JavaSoftwareProcessDriver {

    Integer getGossipPort();

    Integer getSslGossipPort();

    Integer getThriftPort();

    String getClusterName();

}
