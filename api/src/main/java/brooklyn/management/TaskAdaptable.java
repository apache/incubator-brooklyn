package brooklyn.management;

/** marker interface for something which can be adapted to a task  */
public interface TaskAdaptable<T> {
    Task<T> asTask();
}
