package brooklyn.location.basic.jclouds.pool;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Processor;
import org.jclouds.domain.Location;

import com.google.common.base.Function;

public class MachinePoolPredicates {

    public static Function<MachineSet, MachineSet> except(final MachineSet removedItems) {
        return new Function<MachineSet, MachineSet>() {
            @Override
            public MachineSet apply(MachineSet universe) {
                return universe.removed(removedItems);
            }
        };
    }

    public static Function<MachineSet, MachineSet> except(final Function<MachineSet, MachineSet> removedItemsFilter) {
        return new Function<MachineSet, MachineSet>() {
            @Override
            public MachineSet apply(MachineSet universe) {
                return except(removedItemsFilter.apply(universe)).apply(universe);
            }
        };
    }

    public static Function<MachineSet, MachineSet> matching(final ReusableMachineTemplate template) {
        return new Function<MachineSet, MachineSet>() {
            @Override
            public MachineSet apply(MachineSet universe) {
                Set<NodeMetadata> s = new LinkedHashSet();
                for (NodeMetadata m: universe) {
                    if (matches(template, m))
                        s.add(m);
                }
                return new MachineSet(s);
            }
        };
    }

    public static Function<MachineSet, MachineSet> withTag(final String tag) {
        return new Function<MachineSet, MachineSet>() {
            @Override
            public MachineSet apply(MachineSet universe) {
                Set<NodeMetadata> s = new LinkedHashSet();
                for (NodeMetadata m: universe)
                    if (m.getTags().contains(tag))
                        s.add(m);
                return new MachineSet(s);
            }
        };
    }

    public static Function<MachineSet, MachineSet> compose(final Function<MachineSet, MachineSet> ...ops) {
        return new Function<MachineSet, MachineSet>() {
            @Override
            public MachineSet apply(MachineSet set) {
                for (int i=ops.length-1; i>=0; i--)
                    set = ops[i].apply(set);
                return set;
            }
        };
        
    }

    /** True iff the node matches the criteria specified in this template. 
     * <p>
     * NB: This only checks some of the most common fields, 
     * plus a hashcode (in strict mode).  
     * In strict mode you're practically guaranteed to match only machines created by this template.
     * (Add a tag(uid) and you _will_ be guaranteed, strict mode or not.)
     * <p> 
     * Outside strict mode, some things (OS and hypervisor) can fall through the gaps.  
     * But if that is a problem we can easily add them in.
     * <p>
     * (Caveat: If explicit Hardware, Image, and/or Template were specified in the template,
     * then the hash code probably will not detect it.)   
     **/
    public static boolean matches(ReusableMachineTemplate template, NodeMetadata m) {
        // tags and user metadata
        
        if (! m.getTags().containsAll( template.getTags(false) )) return false;
        
        if (! isSubMapOf(template.getUserMetadata(false), m.getUserMetadata())) return false;

        
        // common hardware parameters

        if (template.getMinRam()!=null && m.getHardware().getRam() < template.getMinRam()) return false;
        
        if (template.getMinCores()!=null) {
            double numCores = 0;
            for (Processor p: m.getHardware().getProcessors()) numCores += p.getCores();
            if (numCores+0.001 < template.getMinCores()) return false;
        }

        if (template.getIs64bit()!=null) {
            if (m.getOperatingSystem().is64Bit() != template.getIs64bit()) return false;
        }
        
        if (template.getLocationId()!=null) {
            if (!isLocationContainedIn(m.getLocation(), template.getLocationId())) return false;
        }

        // TODO other TemplateBuilder fields and TemplateOptions
        
        return true;
    }

    private static boolean isLocationContainedIn(Location location, String locationId) {
        if (location==null) return false;
        if (locationId.equals(location.getId())) return true;
        return isLocationContainedIn(location.getParent(), locationId);
    }

    public static boolean isSubMapOf(Map<String, String> sub, Map<String, String> bigger) {
        for (Map.Entry<String, String> e: sub.entrySet()) {
            if (e.getValue()==null) {
                if (!bigger.containsKey(e.getKey())) return false;
                if (bigger.get(e.getKey())!=null) return false;
            } else {
                if (!e.getValue().equals(bigger.get(e.getKey()))) return false;
            }
        }
        return true;
    }

}
