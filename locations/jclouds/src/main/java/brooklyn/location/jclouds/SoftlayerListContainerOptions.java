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
package brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.inject.Singleton;

import org.jclouds.openstack.swift.blobstore.functions.BlobStoreListContainerOptionsToListContainerOptions;
import org.jclouds.openstack.swift.options.ListContainerOptions;

@Singleton
public class SoftlayerListContainerOptions extends
        BlobStoreListContainerOptionsToListContainerOptions {

    @Override
    public ListContainerOptions apply(
            org.jclouds.blobstore.options.ListContainerOptions from) {
        checkNotNull(from, "set options to instance NONE instead of passing null");
        org.jclouds.openstack.swift.options.ListContainerOptions options = new org.jclouds.openstack.swift.options.ListContainerOptions();
        if ((from.getDir() == null) && (from.isRecursive())) {
           options.withPrefix("");
        }
        if ((from.getDir() == null) && (!from.isRecursive())) {
           options.buildQueryParameters().put("delimiter", "/");
        }
        if ((from.getDir() != null) && (from.isRecursive())) {
           options.withPrefix(from.getDir().endsWith("/") ? from.getDir() : from.getDir() + "/");
        }
        if ((from.getDir() != null) && (!from.isRecursive())) {
           options.withPrefix(from.getDir().endsWith("/") ? from.getDir() : from.getDir() + "/");
           options.buildQueryParameters().put("delimiter", "/");
        }
        if (from.getMarker() != null) {
           options.afterMarker(from.getMarker());
        }
        if (from.getMaxResults() != null) {
           options.maxResults(from.getMaxResults());
        }

        return options;
    }

}
