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
package org.apache.brooklyn.util.internal;

import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.testng.Assert;
import org.testng.annotations.Test;

/** test must not be in {@link JavaClassNames} directory due to exclusion! */
public class JavaClassNamesCallerTest {

    @Test
    public void testCallerIsMe() {
        String result = JavaClassNames.niceClassAndMethod();
        Assert.assertEquals(result, "JavaClassNamesCallerTest.testCallerIsMe");
    }

    @Test
    public void testCallerIsYou() {
        other();
    }
    
    public void other() {
        String result = JavaClassNames.callerNiceClassAndMethod(1);
        Assert.assertEquals(result, "JavaClassNamesCallerTest.testCallerIsYou");
    }
    

}
