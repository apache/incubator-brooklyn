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
package org.apache.brooklyn.util.javalang;

import java.lang.reflect.Field;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;


public class Equals {

    private static final Logger log = LoggerFactory.getLogger(Equals.class);
    
    /** Tests whether the objects given are either all null or all equal to the first argument */
    public static boolean objects(Object o1, Object o2, Object... oo) {
        if (!Objects.equal(o1, o2)) return false;
        for (Object o: oo) 
            if (!Objects.equal(o1, o)) return false;
        return true;
    }

    /** Tests whether the two objects given are either all null or all approximately equal 
     * (tolerance of 0.001 for floating point, but subject to change) */
    // relatively high tolerance mainly due to enrichers such as Tomcat windowed average, in hot standby;
    // could make smaller
    @Beta
    public static boolean approximately(Object o1, Object o2) {
        if (o1 instanceof Number) {
            if (o2 instanceof Number) {
                return Math.abs( ((Number)o2).doubleValue()-((Number)o1).doubleValue() ) < 0.001;
            }
        }
        return Objects.equal(o1, o2);
    }

    /** As {@link #approximately(Object, Object)} but testing all the arguments given. */
    @Beta
    public static boolean approximately(Object o1, Object o2, Object o3, Object... oo) {
        if (!approximately(o1, o2)) return false;
        if (!approximately(o1, o3)) return false;
        for (Object o: oo) 
            if (!approximately(o1, o)) return false;
        return true;        
    }

    /** Useful for debugging EqualsBuilder.reflectionEquals */
    public static void dumpReflectiveEquals(Object o1, Object o2) {
        log.info("Comparing: "+o1+" "+o2);
        Class<?> clazz = o1.getClass();
        while (!(clazz.equals(Object.class))) {
            log.info("  fields in: "+clazz);
            for (Field f: clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    log.info( "    "+(Objects.equal(f.get(o1), f.get(o2)) ? "==" : "!=" ) +
                        " "+ f.getName()+ " "+ f.get(o1) +" "+ f.get(o2) +
                        " ("+ classOf(f.get(o1)) +" "+ classOf(f.get(o2)+")") );
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    log.info( "    <error> "+e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static String classOf(Object o) {
        if (o==null) return null;
        return o.getClass().toString();
    }

}
