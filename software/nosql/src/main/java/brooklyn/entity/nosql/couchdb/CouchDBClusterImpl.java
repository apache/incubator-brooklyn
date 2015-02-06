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
package brooklyn.entity.nosql.couchdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Implementation of {@link CouchDBCluster}.
 */
public class CouchDBClusterImpl extends DynamicClusterImpl implements CouchDBCluster {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(CouchDBClusterImpl.class);

    public CouchDBClusterImpl() {
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the CouchDB nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(CouchDBNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

}
