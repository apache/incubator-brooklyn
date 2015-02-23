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

import org.jclouds.blobstore.strategy.MkdirStrategy;
import org.jclouds.openstack.swift.blobstore.functions.BlobStoreListContainerOptionsToListContainerOptions;
import org.jclouds.openstack.swift.functions.ParseObjectInfoListFromJsonResponse;

import com.google.inject.AbstractModule;

public class MkDirStrategyCustomModule extends AbstractModule  {

    @Override
    protected void configure() {
        // Object Storage v1 API doesn't have the notion of a folder. Just use
        // forward slash delimited prefix for the object being put. There is
        // functionality to to restrict the listing to the next forward slash by
        // using the prefix & delimiter query arguments to the list call.
        //
        // http://developer.openstack.org/api-ref-objectstorage-v1.html
        // http://docs.openstack.org/user-guide/content/managing-openstack-object-storage-with-swift-cli.html#pseudo-hierarchical-folders-directories

        // Don't create folders even if explicitly requested
        bind(MkdirStrategy.class).to(NoopMkdirStrategy.class);

        // Send prefix & delimiter query parameters
        bind(BlobStoreListContainerOptionsToListContainerOptions.class).to(SoftlayerListContainerOptions.class);

        // Support for {"subdir": "xxx"} list items, treat them as "application/directory" entries
        bind(ParseObjectInfoListFromJsonResponse.class).to(ParseList.class);
    }

}
