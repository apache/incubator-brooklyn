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
package org.apache.brooklyn.core.mgmt.rebind;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RebindClassInitializationTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindClassInitializationTest.class);
    static List<String> messages = MutableList.of();
    
    @Test
    public void testRestoresSimpleApp() throws Exception {
        messages.clear();
        messages.add("creating");
        origApp.createAndManageChild(EntitySpec.create(Entity.class, MyEntityForClassInitializationTesting.class));
        messages.add("created");
        messages.add("rebinding");
        newApp = rebind();
        messages.add("rebinded");
        
        log.debug("Create and rebind message sequence is:\n- "+Strings.join(messages, "\n- "));
        Assert.assertEquals(messages, MutableList.of(
            "creating", "ME.static_initializer", "ME.initializer", 
            "WIM.static_initializer", "WIM.initializer", "WIM.constructor", 
            "ME.constructor", "created", 
            "rebinding", "ME.initializer", "WIM.initializer", "WIM.constructor", 
            "ME.constructor", "rebinded"));
    }
    
    public static class MyEntityForClassInitializationTesting extends AbstractEntity {
        
        { messages.add("ME.initializer"); }
        
        static { messages.add("ME.static_initializer"); }
        
        @SuppressWarnings("unused")
        private final Object dummy = new WriteInitMessage();

        public MyEntityForClassInitializationTesting() {
            messages.add("ME.constructor");
        }
    }

    private static class WriteInitMessage {
        public WriteInitMessage() { messages.add("WIM.constructor"); }
        
        { messages.add("WIM.initializer"); }
        
        static { messages.add("WIM.static_initializer"); }
    }
}
