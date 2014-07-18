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
package brooklyn.util.yaml;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

public class Yamls {

    private static final Logger log = LoggerFactory.getLogger(Yamls.class);

    /** returns the given yaml object (map or list or primitive) as the given yaml-supperted type 
     * (map or list or primitive e.g. string, number, boolean).
     * <p>
     * if the object is an iterable containing a single element, and the type is not an iterable,
     * this will attempt to unwrap it.
     * 
     * @throws IllegalArgumentException if the input is an iterable not containing a single element,
     *   and the cast is requested to a non-iterable type 
     * @throws ClassCastException if cannot be casted */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T getAs(Object x, Class<T> type) {
        if (x==null) return null;
        if (x instanceof Iterable || x instanceof Iterator) {
            List result = new ArrayList();
            Iterator xi;
            if (Iterator.class.isAssignableFrom(x.getClass())) {
                xi = (Iterator)x;
            } else {
                xi = ((Iterable)x).iterator();
            }
            while (xi.hasNext()) {
                result.add( xi.next() );
            }
            if (type.isAssignableFrom(Iterable.class)) return (T)result;
            if (type.isAssignableFrom(Iterator.class)) return (T)result.iterator();
            if (type.isAssignableFrom(List.class)) return (T)result;
            x = Iterables.getOnlyElement(result);
        }
        return (T)x;
    }

    @SuppressWarnings("rawtypes")
    public static void dump(int depth, Object r) {
        if (r instanceof Iterable) {
            for (Object ri : ((Iterable)r))
                dump(depth+1, ri);
        } else if (r instanceof Map) {
            for (Object re: ((Map)r).entrySet()) {
                for (int i=0; i<depth; i++) System.out.print(" ");
                System.out.println(((Entry)re).getKey()+":");
                dump(depth+1, ((Entry)re).getValue());
            }
        } else {
            for (int i=0; i<depth; i++) System.out.print(" ");
            if (r==null) System.out.println("<null>");
            else System.out.println("<"+r.getClass().getSimpleName()+">"+" "+r);
        }
    }

    /** simplifies new Yaml().loadAll, and converts to list to prevent single-use iterable bug in yaml */
    @SuppressWarnings("unchecked")
    public static Iterable<Object> parseAll(String yaml) {
        Iterable<Object> result = new org.yaml.snakeyaml.Yaml().loadAll(yaml);
        return (List<Object>) getAs(result, List.class);
    }

    /** as {@link #parseAll(String)} */
    @SuppressWarnings("unchecked")
    public static Iterable<Object> parseAll(Reader yaml) {
        Iterable<Object> result = new org.yaml.snakeyaml.Yaml().loadAll(yaml);
        return (List<Object>) getAs(result, List.class);
    }

    public static Object removeMultinameAttribute(Map<String,Object> obj, String ...equivalentNames) {
        Object result = null;
        for (String name: equivalentNames) {
            Object candidate = obj.remove(name);
            if (candidate!=null) {
                if (result==null) result = candidate;
                else if (!result.equals(candidate)) {
                    log.warn("Different values for attributes "+Arrays.toString(equivalentNames)+"; " +
                    		"preferring '"+result+"' to '"+candidate+"'");
                }
            }
        }
        return result;
    }
}
