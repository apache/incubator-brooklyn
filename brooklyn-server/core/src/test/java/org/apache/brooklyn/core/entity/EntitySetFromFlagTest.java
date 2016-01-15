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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.location.PortRanges;
import org.testng.annotations.Test;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

public class EntitySetFromFlagTest {

    @Test
    public void testSetFromFlagUsingFieldName() {
        MyEntity entity = new MyEntity(MutableMap.of("str1", "myval"));
        assertEquals(entity.str1, "myval");
    }
    
    @Test
    public void testSetFromFlagUsingOverridenName() {
        MyEntity entity = new MyEntity(MutableMap.of("altStr2", "myval"));
        assertEquals(entity.str2, "myval");
    }
    
    @Test
    public void testSetFromFlagWhenNoDefaultIsNull() {
        MyEntity entity = new MyEntity();
        assertEquals(entity.str1, null);
    }
    
    @Test
    public void testSetFromFlagUsesDefault() {
        MyEntity entity = new MyEntity();
        assertEquals(entity.str3, "default str3");
    }
    
    @Test
    public void testSetFromFlagOverridingDefault() {
        MyEntity entity = new MyEntity(MutableMap.of("str3", "overridden str3"));
        assertEquals(entity.str3, "overridden str3");
    }

    @Test
    public void testSetFromFlagCastsPrimitives() {
        MyEntity entity = new MyEntity(MutableMap.of("double1", 1f));
        assertEquals(entity.double1, 1d);
    }

    @Test
    public void testSetFromFlagCastsDefault() {
        MyEntity entity = new MyEntity();
        assertEquals(entity.byte1, (byte)1);
        assertEquals(entity.short1, (short)2);
        assertEquals(entity.int1, 3);
        assertEquals(entity.long1, 4l);
        assertEquals(entity.float1, 5f);
        assertEquals(entity.double1, 6d);
         assertEquals(entity.char1, 'a');
        assertEquals(entity.bool1, true);
        
        assertEquals(entity.byte2, Byte.valueOf((byte)1));
        assertEquals(entity.short2, Short.valueOf((short)2));
        assertEquals(entity.int2, Integer.valueOf(3));
        assertEquals(entity.long2, Long.valueOf(4l));
        assertEquals(entity.float2, Float.valueOf(5f));
        assertEquals(entity.double2, Double.valueOf(6d));
        assertEquals(entity.char2, Character.valueOf('a'));
        assertEquals(entity.bool2, Boolean.TRUE);
    }
    
    @Test
    public void testSetFromFlagCoercesDefaultToPortRange() {
        MyEntity entity = new MyEntity();
        assertEquals(entity.portRange1, PortRanges.fromInteger(1234));
    }
    
    @Test
    public void testSetFromFlagCoercesStringValueToPortRange() {
        MyEntity entity = new MyEntity(MutableMap.of("portRange1", "1-3"));
        assertEquals(entity.portRange1, new PortRanges.LinearPortRange(1, 3));
    }
    
    @Test
    public void testSetFromFlagCoercesStringValueToInt() {
        MyEntity entity = new MyEntity(MutableMap.of("int1", "123"));
        assertEquals(entity.int1, 123);
    }

    @Test
    public void testSetIconUrl() {
        MyEntity entity = new MyEntity(MutableMap.of("iconUrl", "/img/myicon.gif"));
        assertEquals(entity.getIconUrl(), "/img/myicon.gif");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testFailsFastOnInvalidCoercion() {;
        new MyEntity(MutableMap.of("int1", "thisisnotanint"));
    }
    
    // Fails because configure being called from inside constructor; so field is set after configure called
    @Test(enabled=false) 
    public void testSetFromFlagWithFieldThatIsExplicitySet() {
        MyEntity entity = new MyEntity(MutableMap.of("str4", "myval"));
        assertEquals(entity.str4, "myval");
        
        MyEntity entity2 = new MyEntity();
        assertEquals(entity2.str4, "explicit str4");
    }
    
    private static class MyEntity extends AbstractEntity {

        @SetFromFlag(defaultVal="1234")
        PortRange portRange1;

        @SetFromFlag
        String str1;
        
        @SetFromFlag("altStr2")
        String str2;
        
        @SetFromFlag(defaultVal="default str3")
        String str3;

        @SetFromFlag
        String str4 = "explicit str4";
        
        @SetFromFlag(defaultVal="1")
        byte byte1;

        @SetFromFlag(defaultVal="2")
        short short1;

        @SetFromFlag(defaultVal="3")
        int int1;

        @SetFromFlag(defaultVal="4")
        long long1;

        @SetFromFlag(defaultVal="5")
        float float1;

        @SetFromFlag(defaultVal="6")
        double double1;

        @SetFromFlag(defaultVal="a")
        char char1;

        @SetFromFlag(defaultVal="true")
        boolean bool1;

        @SetFromFlag(defaultVal="1")
        Byte byte2;

        @SetFromFlag(defaultVal="2")
        Short short2;

        @SetFromFlag(defaultVal="3")
        Integer int2;

        @SetFromFlag(defaultVal="4")
        Long long2;

        @SetFromFlag(defaultVal="5")
        Float float2;

        @SetFromFlag(defaultVal="6")
        Double double2;

        @SetFromFlag(defaultVal="a")
        Character char2;

        @SetFromFlag(defaultVal="true")
        Boolean bool2;

        MyEntity() {
            super(MutableMap.of(), null);
        }
        
        MyEntity(Map flags) {
            super(flags, null);
        }
        
        MyEntity(Entity parent) {
            super(MutableMap.of(), parent);
        }
        
        MyEntity(Map flags, Entity parent) {
            super(flags, parent);
        }
    }
}
