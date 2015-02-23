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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.SortedSet;

import javax.inject.Inject;

import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.json.Json;
import org.jclouds.openstack.swift.domain.ObjectInfo;
import org.jclouds.openstack.swift.functions.ParseObjectInfoListFromJsonResponse;

import com.google.gson.reflect.TypeToken;

public class ParseList extends ParseObjectInfoListFromJsonResponse {

    @Inject
    public ParseList(Json json) {
        super(json);
    }

    @Override
    public PageSet<ObjectInfo> apply(InputStream stream) {
        return super.apply(stream);
    }

    @Override
    public <V> V apply(InputStream stream, Type type) throws IOException {
        Type listType = new TypeToken<SortedSet<ObjectInfoOrSubdirImpl>>() {
        }.getType();
        return super.apply(stream, listType);
    }
}
