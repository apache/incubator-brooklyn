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
package brooklyn.entity.rebind.dto;

import org.apache.brooklyn.mementos.BrooklynMementoManifest.EntityMementoManifest;

public class EntityMementoManifestImpl implements EntityMementoManifest {
    private String id;
    private String type;
    private String parentId;
    private String catalogItemId;

    public EntityMementoManifestImpl(String id, String type, String parentId, String catalogItemId) {
        this.id = id;
        this.type = type;
        this.parentId = parentId;
        this.catalogItemId = catalogItemId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getParent() {
        return parentId;
    }

    @Override
    public String getCatalogItemId() {
        return catalogItemId;
    }

}
