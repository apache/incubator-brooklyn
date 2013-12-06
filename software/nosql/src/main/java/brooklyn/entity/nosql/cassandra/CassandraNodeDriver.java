/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.entity.java.JavaSoftwareProcessDriver;
import brooklyn.util.task.system.ProcessTaskWrapper;

public interface CassandraNodeDriver extends JavaSoftwareProcessDriver {

    Integer getGossipPort();

    Integer getSslGossipPort();

    Integer getThriftPort();

    Integer getNativeTransportPort();

    String getClusterName();

    String getCassandraConfigTemplateUrl();

    String getCassandraConfigFileName();

    boolean isClustered();

    ProcessTaskWrapper<Integer> executeScriptAsync(String commands);
}
