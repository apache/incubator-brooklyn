package brooklyn.entity.rebind.persister;

public interface MementoSerializer<T> {
    String toString(T memento);
    T fromString(String string);
}