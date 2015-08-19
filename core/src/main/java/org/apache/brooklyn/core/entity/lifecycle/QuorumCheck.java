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
package org.apache.brooklyn.core.entity.lifecycle;

import java.io.Serializable;

/**
 * For checking if a group/cluster is quorate. That is, whether the group has sufficient
 * healthy members.
 * @deprecated since 0.7.0 use {@link org.apache.brooklyn.util.collections.QuorumCheck}. 
 * but keep this for a while as old quorum checks might be persisted. 
 */
@Deprecated
public interface QuorumCheck extends org.apache.brooklyn.util.collections.QuorumCheck {

    /**
     * @param sizeHealthy Number of healthy members
     * @param totalSize   Total number of members one would expect to be healthy (i.e. ignoring stopped members)
     * @return            Whether this group is healthy
     */
    public boolean isQuorate(int sizeHealthy, int totalSize);

    public static class QuorumChecks {
        /**
         * Checks that all members that should be up are up (i.e. ignores stopped nodes).
         */
        public static QuorumCheck all() {
            return new NumericQuorumCheck(0, 1.0, false);
        }
        /**
         * Checks all members that should be up are up, and that there is at least one such member.
         */
        public static QuorumCheck allAndAtLeastOne() {
            return new NumericQuorumCheck(1, 1.0, false);
        }
        /**
         * Requires at least one member that should be up is up.
         */
        public static QuorumCheck atLeastOne() {
            return new NumericQuorumCheck(1, 0.0, false);
        }
        /**
         * Requires at least one member to be up if the total size is non-zero.
         * i.e. okay if empty, or if non-empty and something is healthy, but not okay if not-empty and nothing is healthy.
         * "Empty" means that no members are supposed to be up  (e.g. there may be stopped members).
         */
        public static QuorumCheck atLeastOneUnlessEmpty() {
            return new NumericQuorumCheck(1, 0.0, true);
        }
        /**
         * Always "healthy"
         */
        public static QuorumCheck alwaysTrue() {
            return new NumericQuorumCheck(0, 0.0, true);
        }
        public static QuorumCheck newInstance(int minRequiredSize, double minRequiredRatio, boolean allowEmpty) {
            return new NumericQuorumCheck(minRequiredSize, minRequiredRatio, allowEmpty);
        }
    }
    
    /** @deprecated since 0.7.0 use {@link org.apache.brooklyn.util.collections.QuorumCheck}. 
    * but keep this until we have a transition defined. 
    */
    @Deprecated
    public static class NumericQuorumCheck implements QuorumCheck, Serializable {
        private static final long serialVersionUID = -5090669237460159621L;
        
        protected final int minRequiredSize;
        protected final double minRequiredRatio;
        protected final boolean allowEmpty;

        public NumericQuorumCheck(int minRequiredSize, double minRequiredRatio, boolean allowEmpty) {
            this.minRequiredSize = minRequiredSize;
            this.minRequiredRatio = minRequiredRatio;
            this.allowEmpty = allowEmpty;
        }
        
        @Override
        public boolean isQuorate(int sizeHealthy, int totalSize) {
            if (allowEmpty && totalSize==0) return true;
            if (sizeHealthy < minRequiredSize) return false;
            if (sizeHealthy < totalSize*minRequiredRatio-0.000000001) return false;
            return true;
        }
        
        @Override
        public String toString() {
            return "QuorumCheck[require="+minRequiredSize+","+((int)100*minRequiredRatio)+"%"+(allowEmpty ? "|0" : "")+"]";
        }
    }
    
}
