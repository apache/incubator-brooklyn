package brooklyn.location.basic.jclouds.pool;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.jclouds.compute.domain.NodeMetadata;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

@Immutable
public class MachineSet implements Iterable<NodeMetadata> {

    final Set<NodeMetadata> members;
    
    public MachineSet(Collection<? extends NodeMetadata> m) { 
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
        Set<NodeMetadata> s = new LinkedHashSet(members);
        for (NodeMetadata m: toRemove) s.remove(m);
        return new MachineSet(s);
    }
    public MachineSet added(MachineSet toAdd) {
        Set<NodeMetadata> s = new LinkedHashSet(members);
        for (NodeMetadata m: toAdd) s.add(m);
        return new MachineSet(s);
    }
    
    public MachineSet filtered(Function<MachineSet,MachineSet> ...ops) {
        return MachinePoolPredicates.compose(ops).apply(this);
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
