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
package brooklyn.entity.rebind.transformer;

import static org.testng.Assert.assertTrue;

import java.util.Collection;

import org.apache.brooklyn.api.entity.rebind.BrooklynObjectType;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.transformer.impl.XsltTransformer;

import com.google.common.collect.Iterables;

public class CompoundTransformerLoaderTest {

    @Test
    public void testLoadsTransformerFromYaml() throws Exception {
        String contents =
                "- renameType:\n"+
                "    old_val: myoldname\n"+
                "    new_val: mynewname\n"+
                "- renameClassTag:\n"+
                "    old_val: myoldname\n"+
                "    new_val: mynewname\n"+
                "- renameField:\n"+
                "    class_name: myclassname\n"+
                "    old_val: myoldname\n"+
                "    new_val: mynewname\n"+
                // low-level mechanism to change catalogItemId; used (and tested) by higher-level methods
                // which use symbolic_name:version notation to avoid the unpleasant need for yaml quotes
                "- catalogItemId:\n"+
                "    old_symbolic_name: myclassname\n"+
                "    new_symbolic_name: myclassname\n"+
                "    new_version: '2.0'\n"+
                "- xslt:\n"+
                "    url: classpath://brooklyn/entity/rebind/transformer/impl/renameType.xslt\n"+
                "    substitutions:\n"+
                "      old_val: myoldname\n"+
                "      new_val: mynewname\n"+
                "- rawDataTransformer:\n"+
                "    type: "+MyRawDataTransformer.class.getName()+"\n";
        
        CompoundTransformer transformer = CompoundTransformerLoader.load(contents);
        Collection<RawDataTransformer> rawDataTransformers = transformer.getRawDataTransformers().get(BrooklynObjectType.ENTITY);
        assertTrue(Iterables.get(rawDataTransformers, 0) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 1) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 2) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 3) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 4) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 5) instanceof MyRawDataTransformer);
    }
    
    public static class MyRawDataTransformer implements RawDataTransformer {
        @Override
        public String transform(String input) throws Exception {
            return input; // no-op
        }
    }
}
