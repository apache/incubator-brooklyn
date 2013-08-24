package brooklyn.management;


/** Interface for something which can generate tasks (or task wrappers) */
public interface TaskFactory<T extends TaskAdaptable<?>> {
    T newTask();
}
