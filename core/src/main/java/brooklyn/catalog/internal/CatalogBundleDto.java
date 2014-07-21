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
package brooklyn.catalog.internal;

import com.google.common.base.Objects;

import brooklyn.catalog.CatalogItem.CatalogBundle;

public class CatalogBundleDto implements CatalogBundle {
    private String name;
    private String version;
    private String url;

    public CatalogBundleDto() {}

    public CatalogBundleDto(String name, String version, String url) {
        this.name = name;
        this.version = version;
        this.url = url;
    }

    @Override
    public boolean isNamed() {
        return name != null && version != null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("version", version)
                .add("url", url)
                .toString();
    }
}
