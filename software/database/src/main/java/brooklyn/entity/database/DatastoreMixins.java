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
package brooklyn.entity.database;

import java.io.InputStream;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.text.Strings;

public class DatastoreMixins {

    private DatastoreMixins() {}
    
    
    public static final AttributeSensor<String> DATASTORE_URL = HasDatastoreUrl.DATASTORE_URL;
    
    public static interface HasDatastoreUrl {
        public static final AttributeSensor<String> DATASTORE_URL = Sensors.newStringSensor("datastore.url",
            "Primary contact URL for a datastore (e.g. mysql://localhost:3306/)");
    }

    
    public static final Effector<String> EXECUTE_SCRIPT = CanExecuteScript.EXECUTE_SCRIPT;
    
    public static interface CanExecuteScript {
        public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("executes the given script contents")
            .parameter(String.class, "commands")
            .buildAbstract();
    }

    
    public static final ConfigKey<String> CREATION_SCRIPT_CONTENTS = CanGiveCreationScript.CREATION_SCRIPT_CONTENTS; 
    public static final ConfigKey<String> CREATION_SCRIPT_URL = CanGiveCreationScript.CREATION_SCRIPT_URL; 

    public static interface CanGiveCreationScript {
        @SetFromFlag("creationScriptContents")
        public static final ConfigKey<String> CREATION_SCRIPT_CONTENTS = ConfigKeys.newStringConfigKey("datastore.creation.script.contents", "Contensts of creation script to initialize the datastore", "");
        
        @SetFromFlag("creationScriptUrl")
        public static final ConfigKey<String> CREATION_SCRIPT_URL = ConfigKeys.newStringConfigKey("datastore.creation.script.url", "URL of creation script to use to initialize the datastore (ignored if contents are specified)", "");
    }

    /** returns the creation script contents, if it exists, or null if none is defined (error if it cannot be loaded) */
    @Nullable public static InputStream getDatabaseCreationScript(Entity entity) {
        String url = entity.getConfig(DatastoreMixins.CREATION_SCRIPT_URL);
        if (!Strings.isBlank(url))
            return new ResourceUtils(entity).getResourceFromUrl(url);
        String contents = entity.getConfig(DatastoreMixins.CREATION_SCRIPT_CONTENTS);
        if (!Strings.isBlank(contents))
            return KnownSizeInputStream.of(contents);
        return null;
    }

    
    /** An entity with the most common datastore config, sensors, and effectors */ 
    public interface DatastoreCommon extends Entity, DatastoreMixins.HasDatastoreUrl, DatastoreMixins.CanExecuteScript, DatastoreMixins.CanGiveCreationScript {
    }

}
