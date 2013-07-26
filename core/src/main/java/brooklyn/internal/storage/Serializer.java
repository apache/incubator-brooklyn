package brooklyn.internal.storage;

public interface Serializer<S, T> {

    T serialize(S orig);
    
    S deserialize(T serializedForm);
}
