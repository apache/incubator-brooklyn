package brooklyn.management;

/** marks a place where tasks can be added, e.g. a task which allows children to be added (including after it is activated) */
public interface CanAddTask {

    public void addTask(Task<?> t);
    
}
