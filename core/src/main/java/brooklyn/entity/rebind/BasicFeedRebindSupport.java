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
package brooklyn.entity.rebind;

import org.apache.brooklyn.mementos.FeedMemento;

import brooklyn.event.feed.AbstractFeed;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

public class BasicFeedRebindSupport extends AbstractBrooklynObjectRebindSupport<FeedMemento> {

    private final AbstractFeed feed;
    
    public BasicFeedRebindSupport(AbstractFeed feed) {
        super(feed);
        this.feed = feed;
    }
    
    @Override
    protected void addConfig(RebindContext rebindContext, FeedMemento memento) {
        // TODO entity does config-lookup differently; the memento contains the config keys.
        // BasicEntityMemento.postDeserialize uses the injectTypeClass to call EntityTypes.getDefinedConfigKeys(clazz)
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(feed, configBag);
        FlagUtils.setAllConfigKeys(feed, configBag, false);
    }
    
    @Override
    protected void addCustoms(RebindContext rebindContext, FeedMemento memento) {
        // no-op
    }
}
