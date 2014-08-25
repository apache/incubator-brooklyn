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
package brooklyn.entity.proxy;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;

/**
 * A cluster of load balancers, where configuring the cluster (through the LoadBalancer interface)
 * will configure all load balancers in the cluster.
 * 
 * Config keys (such as LoadBalancer.serverPool and LoadBalancer.urlMappings) are automatically
 * inherited by the children of the load balancer cluster. It is through that mechanism that
 * configuration changes on the cluster will be applied to all child load balancers (i.e. by
 * them all sharing the same serverPool and urlMappings etc).
 *  
 * @author aled
 */
public class LoadBalancerClusterImpl extends DynamicClusterImpl implements LoadBalancerCluster {

    // TODO I suspect there are races with reconfiguring the load-balancers while
    // the cluster is growing: there is no synchronization around the calls to reload
    // and the resize, so presumably there's a race where a newly added load-balancer 
    // could miss the most recent reload call?

    public LoadBalancerClusterImpl() {
        super();
    }

    /* NOTE The following methods come from {@link LoadBalancer} but are probably safe to ignore */
    
    @Override
    public void reload() {
        for (Entity member : getMembers()) {
            if (member instanceof LoadBalancer) {
                ((LoadBalancer)member).reload();
            }
        }
    }

    @Override
    public void update() {
        for (Entity member : getMembers()) {
            if (member instanceof LoadBalancer) {
                ((LoadBalancer)member).update();
            }
        }
    }

    @Override
    public void bind(Map flags) {
        for (Entity member : getMembers()) {
            if (member instanceof LoadBalancer) {
                ((LoadBalancer)member).bind(flags);
            }
        }
    }
}
