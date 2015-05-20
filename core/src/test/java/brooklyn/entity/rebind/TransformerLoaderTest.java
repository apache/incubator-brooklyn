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

import static org.testng.Assert.assertEquals;

import java.util.Collection;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.transformer.RawDataTransformer;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

public class TransformerLoaderTest {

    @Test
    public void testLoadsFromAppClassLoader() throws Exception {
        LocalManagementContext mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).build();
        Collection<RawDataTransformer> transformers = new TransformerLoader(mgmt).findGlobalTransformers();
        assertEquals(transformers.size(), 2, "One transformer in core, one in osgi bundle");
        for (RawDataTransformer t : transformers) {
            assertEquals(t.transform("test"), t.getClass().getSimpleName());
        }
    }
    
}
