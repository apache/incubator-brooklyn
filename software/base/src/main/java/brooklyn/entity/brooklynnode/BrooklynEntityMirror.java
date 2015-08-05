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
package brooklyn.entity.brooklynnode;

import java.util.Map;

import org.apache.brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.time.Duration;

/** Provides an entity which can sit in one brooklyn domain and reflect the status of an entity 
 * via the REST API of another domain.
 * <p>
 * Note tests for this depend on a REST server so are in other projects; search for *Mirror*Test,
 * as well as *BrooklynNode*Test. */
@Catalog(name="Brooklyn Entity Mirror", description="Provides an entity which can sit in one brooklyn "
        + "domain and reflect the status of an entity via the REST API of another domain.")
@ImplementedBy(BrooklynEntityMirrorImpl.class)
public interface BrooklynEntityMirror extends Entity {

    // caller must specify this:
    public static final ConfigKey<String> MIRRORED_ENTITY_URL = ConfigKeys.newStringConfigKey("brooklyn.mirror.entity_url",
        "URL for the entity in the remote Brooklyn mgmt endpoint");
    
    // caller may specify this for reference:
    public static final ConfigKey<String> MIRRORED_ENTITY_ID = ConfigKeys.newStringConfigKey("brooklyn.mirror.entity_id",
        "Brooklyn ID of the entity being mirrored");
    
    // must be specified if required (could be inherited if parent/config is available at init time, but it's not currently)
    public static final ConfigKey<String> MANAGEMENT_USER = BrooklynNode.MANAGEMENT_USER;
    public static final ConfigKey<String> MANAGEMENT_PASSWORD = BrooklynNode.MANAGEMENT_PASSWORD;
    
    public static final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(Duration.class, "brooklyn.mirror.poll_period",
        "Frequency to poll for client sensors", Duration.FIVE_SECONDS);
    
    public static final AttributeSensor<String> MIRROR_STATUS = Sensors.newStringSensor("brooklyn.mirror.monitoring_status");
    @SuppressWarnings("rawtypes")
    public static final AttributeSensor<Map> MIRROR_SUMMARY = Sensors.newSensor(Map.class, "brooklyn.mirror.summary",
        "The json map returned by the entity rest endpoint (ie the EntitySummary model)");
    public static final AttributeSensor<String> MIRROR_CATALOG_ITEM_ID = Sensors.newStringSensor("brooklyn.mirror.catalog_item_id",
        "The catalog item id of the mirrored entity in the remote brooklyn");
    
    public EntityHttpClient http();

}
