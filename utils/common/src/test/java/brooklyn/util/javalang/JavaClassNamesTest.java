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
package brooklyn.util.javalang;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.reflect.TypeToken;

public class JavaClassNamesTest {

    @Test
    public void testType() {
        Assert.assertEquals(JavaClassNames.type(this), JavaClassNamesTest.class);
        Assert.assertEquals(JavaClassNames.type(JavaClassNamesTest.class), JavaClassNamesTest.class);
    }
    
    @Test
    public void testPackage() {
        Assert.assertEquals(JavaClassNames.packageName(JavaClassNamesTest.class), "brooklyn.util.javalang");
        Assert.assertEquals(JavaClassNames.packagePath(JavaClassNamesTest.class), "/brooklyn/util/javalang/");
    }
    
    @Test
    public void testResolveName() {
        Assert.assertEquals(JavaClassNames.resolveName(this, "foo.txt"), "/brooklyn/util/javalang/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(JavaClassNamesTest.class, "foo.txt"), "/brooklyn/util/javalang/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "/foo.txt"), "/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "/bar/foo.txt"), "/bar/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "bar/foo.txt"), "/brooklyn/util/javalang/bar/foo.txt");
    }

    @Test
    public void testResolveClasspathUrls() {
        Assert.assertEquals(JavaClassNames.resolveClasspathUrl(this, "foo.txt"), "classpath://brooklyn/util/javalang/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveClasspathUrl(JavaClassNamesTest.class, "/foo.txt"), "classpath://foo.txt");
        Assert.assertEquals(JavaClassNames.resolveClasspathUrl(JavaClassNamesTest.class, "http://localhost/foo.txt"), "http://localhost/foo.txt");
    }

    @Test
    public void testSimpleClassNames() {
        Assert.assertEquals(JavaClassNames.simpleClassName(this), "JavaClassNamesTest");
        Assert.assertEquals(JavaClassNames.simpleClassName(JavaClassNames.class), JavaClassNames.class.getSimpleName());
        Assert.assertEquals(JavaClassNames.simpleClassName(TypeToken.of(JavaClassNames.class)), JavaClassNames.class.getSimpleName());
        
        Assert.assertEquals(JavaClassNames.simpleClassName(JavaClassNames.class.getSimpleName()), "String");
        Assert.assertEquals(JavaClassNames.simplifyClassName(JavaClassNames.class.getSimpleName()), JavaClassNames.class.getSimpleName());
        
        Assert.assertEquals(JavaClassNames.simpleClassName(1), "Integer");
        Assert.assertEquals(JavaClassNames.simpleClassName(new String[][] { }), "String[][]");
        
        // primitives?
        Assert.assertEquals(JavaClassNames.simpleClassName(new int[] { 1, 2, 3 }), "int[]");
        
        // anonymous
        String anon1 = JavaClassNames.simpleClassName(new Object() {});
        Assert.assertTrue(anon1.startsWith(JavaClassNamesTest.class.getName()+"$"), "anon class is: "+anon1);
    }
}
