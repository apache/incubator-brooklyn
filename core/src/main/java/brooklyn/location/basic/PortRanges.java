package brooklyn.location.basic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import brooklyn.location.PortRange;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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

    /** @deprecated since 0.6.0; use LinearPortRange */
    @Deprecated
    public static class BasicPortRange extends LinearPortRange {
		private static final long serialVersionUID = 2604690520893353582L;
		public static final int MAX_PORT = PortRanges.MAX_PORT;
        public static final PortRange ANY_HIGH_PORT = PortRanges.ANY_HIGH_PORT;
        public BasicPortRange(int start, int end) { super(start, end); }
        @Override
        public String toString() {
            return //getClass().getName()+"["+
                    start+"-"+end; //+"]";
        }
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof BasicPortRange) && toString().equals(obj.toString());
        }
        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }
    
    public static class LinearPortRange implements PortRange, Serializable {
		private static final long serialVersionUID = -9165280509363743508L;
		
		final int start, end, delta;
        private LinearPortRange(int start, int end, int delta) {
            this.start = start;
            this.end = end;
            this.delta = delta;
            assert delta!=0;
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
    
    public static PortRange fromCollection(Collection<?> c) {
        List<PortRange> l = new ArrayList<PortRange>();
        for (Object o: c) {
            if (o instanceof Integer) l.add(fromInteger((Integer)o));
            else if (o instanceof String) l.add(fromString((String)o));
            else if (o instanceof Collection) l.add(fromCollection((Collection<?>)o));
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

    /** performs the language extensions required for this project */
    @SuppressWarnings("rawtypes")
    public static void init() {
        TypeCoercions.registerAdapter(Integer.class, PortRange.class, new Function<Integer,PortRange>() {
            public PortRange apply(Integer x) { return fromInteger(x); }
        });
        TypeCoercions.registerAdapter(String.class, PortRange.class, new Function<String,PortRange>() {
            public PortRange apply(String x) { return fromString(x); }
        });
        TypeCoercions.registerAdapter(Collection.class, PortRange.class, new Function<Collection,PortRange>() {
            public PortRange apply(Collection x) { return fromCollection(x); }
        });
    }
    
    static {
        init();
    }
    
}
