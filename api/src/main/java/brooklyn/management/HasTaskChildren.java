package brooklyn.management;

public interface HasTaskChildren {

    @SuppressWarnings("rawtypes")
    public Iterable<Task> getChildrenTasks();
    
}
