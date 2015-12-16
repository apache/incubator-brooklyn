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
package org.apache.brooklyn.core.location;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;

public class PortRanges {

    public static final int MAX_PORT = 65535;
    public static final PortRange ANY_HIGH_PORT = new LinearPortRange(1024, MAX_PORT);
    
    public static class SinglePort implements PortRange, Serializable {
        private static final long serialVersionUID = 7446781416534230401L;
        
        final int port;
        private SinglePort(int port) { this.port = port; }
        
        @Override
        public Iterator<Integer> iterator() {
            return Collections.singletonList(port).iterator();
        }
        @Override
        public boolean isEmpty() {
            return false;
        }
        @Override
        public boolean asBoolean() {
            return true;
        }
        @Override
        public String toString() {
            return //getClass().getName()+"["+
                    ""+port; //+"]";
        }
        public int hashCode() {
            return Objects.hashCode(port);
        }
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof SinglePort) && port == ((SinglePort)obj).port;
        }
    }

    public static class LinearPortRange implements PortRange, Serializable {
        private static final long serialVersionUID = -9165280509363743508L;
        
        final int start, end, delta;
        private LinearPortRange(int start, int end, int delta) {
            this.start = start;
            this.end = end;
            this.delta = delta;
            checkArgument(start > 0 && start <= MAX_PORT, "start port %s out of range", start);
            checkArgument(end > 0 && end <= MAX_PORT, "end port %s out of range", end);
            checkArgument(delta > 0 ? start <= end : start >= end, "start and end out of order: %s to %s, delta %s", start, end, delta);
            checkArgument(delta != 0, "delta must be non-zero");
        }
        public LinearPortRange(int start, int end) {
            this(start, end, (start<=end?1:-1));
        }
        
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                int next = start;
                boolean hasNext = true;
                
                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public Integer next() {
                    if (!hasNext)
                        throw new NoSuchElementException("Exhausted available ports");
                    int result = next;
                    next += delta;
                    if ((delta>0 && next>end) || (delta<0 && next<end)) hasNext = false;
                    return result;
                }
                
                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
        
        @Override
        public boolean isEmpty() {
            return false;
        }
        @Override
        public boolean asBoolean() {
            return true;
        }
        @Override
        public String toString() {
            return //getClass().getName()+"["+
                    start+"-"+end; //+"]";
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(start, end, delta);
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LinearPortRange)) return false;
            LinearPortRange o = (LinearPortRange) obj;
            return start == o.start && end == o.end && delta == o.delta;
        }
    }
    
    public static class AggregatePortRange implements PortRange, Serializable {
        private static final long serialVersionUID = 7332682500816739660L;
        
        final List<PortRange> ranges;
        private AggregatePortRange(List<PortRange> ranges) {
            this.ranges = ImmutableList.copyOf(ranges);
        }
        @Override
        public Iterator<Integer> iterator() {
            return Iterables.concat(ranges).iterator();
        }
        @Override
        public boolean isEmpty() {
            for (PortRange r: ranges)
                if (!r.isEmpty()) return false;
            return true;
        }
        @Override
        public boolean asBoolean() {
            return !isEmpty();
        }
        @Override
        public String toString() {
            String s = "";
            for (PortRange r: ranges) {
                if (s.length()>0) s+=",";
                s += r;
            }
            return //getClass().getName()+"["+
                s; //+"]";
        }
        public int hashCode() {
            return Objects.hashCode(ranges);
        }
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof AggregatePortRange) && ranges.equals(((AggregatePortRange)obj).ranges);
        }
    }

    public static PortRange fromInteger(int x) {
        return new SinglePort(x);
    }
    
    public static PortRange fromIterable(Iterable<?> c) {
        List<PortRange> l = new ArrayList<PortRange>();
        for (Object o: c) {
            if (o instanceof Integer) l.add(fromInteger((Integer)o));
            else if (o instanceof String)
                for (String string : JavaStringEscapes.unwrapJsonishListIfPossible((String)o))
                    l.add(fromString(string));
            else if (o instanceof Iterable) l.add(fromIterable((Iterable<?>)o));
            else if (o instanceof int[]) l.add(fromIterable(Ints.asList((int[])o)));
            else if (o instanceof String[])
                for (String string : (String[])o)
                    l.add(fromString(string));
            else if (o instanceof Object[])
                for (Object object : (Object[])o)
                    if (object instanceof Integer)
                        l.add(fromInteger((Integer)object));
                    else if (object instanceof String)
                        l.add(fromString((String)object));
                    else
                        throw new IllegalArgumentException("'" + object + "' must be of type Integer or String");
            else l.add(TypeCoercions.coerce(o, PortRange.class));
        }
        return new AggregatePortRange(l);
    }

    /** parses a string representation of ports, as "80,8080,8000,8080-8099" */
    public static PortRange fromString(String s) {
        List<PortRange> l = new ArrayList<PortRange>();
        for (String si: s.split(",")) {
            si = si.trim();
            int start, end;
            if (si.endsWith("+")) {
                String si2 = si.substring(0, si.length()-1).trim();
                start = Integer.parseInt(si2);
                end = MAX_PORT;
            } else if (si.indexOf('-')>0) {
                int v = si.indexOf('-');
                start = Integer.parseInt(si.substring(0, v).trim());
                end = Integer.parseInt(si.substring(v+1).trim());
            } else if (si.length()==0) {
                //nothing, ie empty range, just continue
                continue;
            } else {
                //should be number on its own
                l.add(new SinglePort(Integer.parseInt(si)));
                continue;
            }
            l.add(new LinearPortRange(start, end));
        }
        if (l.size() == 1) {
            return l.get(0);
        } else {
            return new AggregatePortRange(l);
        }
    }

    private static AtomicBoolean initialized = new AtomicBoolean(false); 
    /** performs the language extensions required for this project */
    @SuppressWarnings("rawtypes")
    public static void init() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            TypeCoercions.registerAdapter(Integer.class, PortRange.class, new Function<Integer,PortRange>() {
                public PortRange apply(Integer x) { return fromInteger(x); }
            });
            TypeCoercions.registerAdapter(String.class, PortRange.class, new Function<String,PortRange>() {
                public PortRange apply(String x) { return fromString(x); }
            });
            TypeCoercions.registerAdapter(Iterable.class, PortRange.class, new Function<Iterable,PortRange>() {
                public PortRange apply(Iterable x) { return fromIterable(x); }
            });
            initialized.set(true);
        }
    }
    
    static {
        init();
    }
    
}
