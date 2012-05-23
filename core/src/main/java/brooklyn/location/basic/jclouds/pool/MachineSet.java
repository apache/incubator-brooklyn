package brooklyn.location.basic.jclouds.pool;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.jclouds.compute.domain.NodeMetadata;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

@Immutable
public class MachineSet implements Iterable<NodeMetadata> {

    final Set<NodeMetadata> members;
    
    public MachineSet(Iterable<? extends NodeMetadata> m) { 
        members = ImmutableSet.copyOf(m); 
    }
    public MachineSet(NodeMetadata ...nodes) {
        members = ImmutableSet.copyOf(nodes); 
    }

    @Override
    public Iterator<NodeMetadata> iterator() {
        return members.iterator();
    }

    public MachineSet removed(MachineSet toRemove) {
        Set<NodeMetadata> s = new LinkedHashSet<NodeMetadata>(members);
        for (NodeMetadata m: toRemove) s.remove(m);
        return new MachineSet(s);
    }
    public MachineSet added(MachineSet toAdd) {
        Set<NodeMetadata> s = new LinkedHashSet<NodeMetadata>(members);
        for (NodeMetadata m: toAdd) s.add(m);
        return new MachineSet(s);
    }

    @SuppressWarnings("unchecked")
    public MachineSet filtered(Predicate<NodeMetadata> criterion) {
        // To avoid generics complaints in callers caused by varargs, overload here
        return filtered(new Predicate[] {criterion});
    }

    public MachineSet filtered(Predicate<NodeMetadata> ...criteria) {
        return new MachineSet(Iterables.filter(members, MachinePoolPredicates.compose(criteria)));
    }

    public int size() {
        return members.size();
    }
    
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    public boolean contains(NodeMetadata input) {
        return members.contains(input);
    }
    
    @Override
    public String toString() {
        return members.toString();
    }
    
    @Override
    public int hashCode() {
        return members.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MachineSet) && (members.equals( ((MachineSet)obj).members ));
    }
    
}
