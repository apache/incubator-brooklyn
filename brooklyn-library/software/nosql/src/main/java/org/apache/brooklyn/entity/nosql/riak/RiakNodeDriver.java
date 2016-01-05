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
package org.apache.brooklyn.entity.nosql.riak;

import java.util.List;

import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;

public interface RiakNodeDriver extends SoftwareProcessDriver {

    String getRiakEtcDir();

    void joinCluster(String nodeName);

    void leaveCluster();

    void removeNode(String nodeName);

    void recoverFailedNode(String nodeName);

    String getOsMajorVersion();

    void bucketTypeCreate(String bucketTypeName, String bucketTypeProperties);

    List<String> bucketTypeList();

    List<String> bucketTypeStatus(String bucketTypeName);

    void bucketTypeUpdate(String bucketTypeName, String bucketTypeProperties);

    void bucketTypeActivate(String bucketTypeName);
}
