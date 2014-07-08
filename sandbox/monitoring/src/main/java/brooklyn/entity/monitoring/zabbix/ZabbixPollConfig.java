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
package brooklyn.entity.monitoring.zabbix;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;

public class ZabbixPollConfig<T> extends PollConfig<HttpToolResponse, T, ZabbixPollConfig<T>> {

    private String itemKey;

    public ZabbixPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        // Add onSuccess method to extract the last value of the item
        // FIXME Fix generics
        onSuccess((Function)HttpValueFunctions.chain(
                HttpValueFunctions.jsonContents(),
                new Function<JsonElement, JsonElement>() {
                    @Override
                    public JsonElement apply(@Nullable JsonElement input) {
                        Preconditions.checkNotNull(input, "JSON input");
                        return input.getAsJsonObject().get("result")
                                .getAsJsonArray().get(0)
                                .getAsJsonObject().get("lastvalue");
                    }
                },
                JsonFunctions.cast(getSensor().getType())));
    }

    public ZabbixPollConfig(ZabbixPollConfig<T> other) {
        super(other);
        this.itemKey = other.getItemKey();
    }

    public String getItemKey() {
        return itemKey;
    }

    public ZabbixPollConfig<T> itemKey(String val) {
        this.itemKey = val;
        return this;
    }

}
