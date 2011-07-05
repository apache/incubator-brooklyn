package brooklyn.event.adapter

public interface ValueProvider<T> {

    public T compute();
        
}
