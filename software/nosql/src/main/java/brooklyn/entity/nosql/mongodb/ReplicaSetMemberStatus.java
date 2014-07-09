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
package brooklyn.entity.nosql.mongodb;

/**
 * @see <a href="http://docs.mongodb.org/manual/reference/replica-status/">Replica set status reference</a>
 */
public enum ReplicaSetMemberStatus {

    STARTUP("Start up, phase 1 (parsing configuration)"),
    PRIMARY("Primary"),
    SECONDARY("Secondary"),
    RECOVERING("Member is recovering (initial sync, post-rollback, stale members)"),
    FATAL("Member has encountered an unrecoverable error"),
    STARTUP2("Start up, phase 2 (forking threads)"),
    UNKNOWN("Unknown (the set has never connected to the member)"),
    ARBITER("Member is an arbiter"),
    DOWN("Member is not accessible to the set"),
    ROLLBACK("Member is rolling back data. See rollback"),
    SHUNNED("Member has been removed from replica set");

    private final String description;

    ReplicaSetMemberStatus(String description) {
        this.description = description;
    }

    public static ReplicaSetMemberStatus fromCode(int code) {
        switch (code) {
            case 0: return STARTUP;
            case 1: return PRIMARY;
            case 2: return SECONDARY;
            case 3: return RECOVERING;
            case 4: return FATAL;
            case 5: return STARTUP2;
            case 6: return UNKNOWN;
            case 7: return ARBITER;
            case 8: return DOWN;
            case 9: return ROLLBACK;
            case 10: return SHUNNED;
            default: return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return name() + ": " + description;
    }

}
