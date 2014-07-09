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
package brooklyn.entity.webapp;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class FilenameToWebContextMapperTest {

//    *   either ROOT.WAR or /       denotes root context
//    * <p>
//    *   anything of form  FOO.?AR  (ending .?AR) is copied with that name (unless copying not necessary)
//    *                              and is expected to be served from /FOO
//    * <p>
//    *   anything of form  /FOO     (with leading slash) is expected to be served from /FOO
//    *                              (and is copied as FOO.WAR)
//    * <p>
//    *   anything of form  FOO      (without a dot) is expected to be served from /FOO
//    *                              (and is copied as FOO.WAR)
//    * <p>                            
//    *   otherwise <i>please note</i> behaviour may vary on different appservers;
//    *   e.g. FOO.FOO would probably be ignored on appservers which expect a file copied across (usually),
//    *   but served as /FOO.FOO on systems that take a deployment context.

    FilenameToWebContextMapper m = new FilenameToWebContextMapper();
    
    private void assertMapping(String input, String context, String filename) {
        Assert.assertEquals(m.convertDeploymentTargetNameToContext(input), context);
        Assert.assertEquals(m.convertDeploymentTargetNameToFilename(input), filename);
    }
    
    public void testRootNames() {
        assertMapping("/", "/", "ROOT.war");
        assertMapping("ROOT.war", "/", "ROOT.war");
        
        //bad ones -- some of these aren't invertible
        assertMapping("/ROOT.war", "/ROOT.war", "ROOT.war.war");
        assertMapping("/ROOT", "/ROOT", "ROOT.war");
        
        //and leave empty string alone (will cause subsequent error)
        assertMapping("", "", "");
    }

    public void testOtherNames() {
        assertMapping("/foo", "/foo", "foo.war");
        assertMapping("/foo.foo", "/foo.foo", "foo.foo.war");
        assertMapping("foo.war", "/foo", "foo.war");
        assertMapping("foo.Ear", "/foo", "foo.Ear");
        assertMapping("foo", "/foo", "foo.war");
        
        //bad ones -- some of these aren't invertible
        assertMapping("foo.foo", "/foo.foo", "foo.foo");
    }
    
    public void testInferFromUrl() {
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/simple.war", false), "simple.war");
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/simple.Ear?type=raw", false), "simple.Ear");
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/simple.war?type=raw*other=sample.war", false), "simple.war");
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/get?file=simple.war", false), "simple.war");
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/get?file=simple.war&other=ignore", false), "simple.war");
        //takes the first (but logs warning in verbose mode)
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/get?file=simple.war&other=sample.war", false), "simple.war");
        //allows hyphen
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/get?file=simple-simon.war&other=sample", false), "simple-simon.war");
        Assert.assertEquals(m.findArchiveNameFromUrl("http//localhost/get?file=simple\\simon.war&other=sample", false), "simon.war");
    }

}
