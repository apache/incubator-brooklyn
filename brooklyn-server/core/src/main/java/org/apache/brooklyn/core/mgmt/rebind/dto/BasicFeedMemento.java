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
package org.apache.brooklyn.core.mgmt.rebind.dto;

import java.io.Serializable;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.rebind.mementos.FeedMemento;
import org.apache.brooklyn.core.config.Sanitizer;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;

/**
 * The persisted state of a feed.
 *
 * @author aled
 */
public class BasicFeedMemento extends AbstractMemento implements FeedMemento, Serializable {

    private static final long serialVersionUID = -2887448240614023137L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected Map<String,Object> config = Maps.newLinkedHashMap();

        public Builder from(FeedMemento other) {
            super.from(other);
            config.putAll(other.getConfig());
            return this;
        }
        public Builder config(Map<String,?> vals) {
            config.putAll(vals); return this;
        }
        public FeedMemento build() {
            return new BasicFeedMemento(this);
        }
    }

    private Map<String,Object> config;
    private Map<String, Object> fields;

    @SuppressWarnings("unused") // For deserialisation
    private BasicFeedMemento() {}

    // Trusts the builder to not mess around with mutability after calling build()
    protected BasicFeedMemento(Builder builder) {
        super(builder);
        config = toPersistedMap(builder.config);
    }

    @Deprecated
    @Override
    protected void setCustomFields(Map<String, Object> fields) {
        this.fields = toPersistedMap(fields);
    }

    @Deprecated
    @Override
    public Map<String, Object> getCustomFields() {
        return fromPersistedMap(fields);
    }

    @Override
    public Map<String, Object> getConfig() {
        return fromPersistedMap(config);
    }

    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper().add("config", Sanitizer.sanitize(getConfig()));
    }
}
