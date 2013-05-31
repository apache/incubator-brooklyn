package brooklyn.internal.storage.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class LiveSet<T> implements Set<T> {

    protected static interface Mutator<T> {
        public Set<T> refresh();
        public boolean add(T o);
        public boolean addAll(Collection<? extends T> c);
        public boolean remove(Object o);
        public boolean removeAll(Collection<?> c);
        public boolean retainAll(Collection<?> c);
        public void clear();
    }
    
    protected static class BasicMutator<T> implements Mutator<T> {
        @Override public Set<T> refresh() {
            throw new UnsupportedOperationException();
        }
        @Override public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean add(T o) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }
        @Override public void clear() {
            throw new UnsupportedOperationException();
        }
    };

    private final Mutator<T> mutator;
    
    public LiveSet(Mutator<T> mutator) {
        this.mutator = mutator;
    }

    @Override
    public boolean contains(Object o) {
        return mutator.refresh().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return mutator.refresh().containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        return mutator.refresh().isEmpty();
    }

    @Override
    public Object[] toArray() {
        return mutator.refresh().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return mutator.refresh().toArray(a);
    }
    
    @Override
    public int size() {
        return mutator.refresh().size();
    }
    
    @Override
    public boolean add(T o) {
        return mutator.add(o);
    }
    
    @Override
    public boolean remove(Object o) {
        return mutator.remove(o);
    }
    
    @Override
    public boolean removeAll(Collection<?> c) {
        return mutator.removeAll(c);
    }
    
    @Override
    public boolean retainAll(Collection<?> c) {
        return mutator.retainAll(c);
    }
    
    @Override
    public void clear() {
        mutator.clear();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return mutator.addAll(c);
    }
    
    @Override
    public Iterator<T> iterator() {
        final Iterator<T> iterator = mutator.refresh().iterator();
        
        return new Iterator<T>() {
            boolean hasCurrent = false;
            T current = null;
            
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }
            @Override
            public T next() {
                T result = iterator.next();
                current = result;
                hasCurrent = true;
                return result;
            }
            @Override
            public void remove() {
                if (!hasCurrent) {
                    // FIXME check what message/exception type this should be
                    throw new IllegalStateException("Iterator not pointing at anything");
                }
                mutator.remove(current);
                hasCurrent = false;
            }
        };
    }
    
    @Override
    public boolean equals(Object other) {
        // TODO Too expensive to do lookup on every call?
        return other instanceof Set && mutator.refresh().equals(other);
    }
    
    @Override
    public int hashCode() {
        // TODO Too expensive to do lookup on every call?
        return mutator.refresh().hashCode();
    }
    
    @Override
    public String toString() {
        // TODO Too expensive to do lookup on every call?
        return mutator.refresh().toString();
    }
}
