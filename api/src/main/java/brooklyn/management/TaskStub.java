package brooklyn.management;

/**
 * @deprecated since 0.5; May be deleted (still up for discussion); one can use {@link Task} directly
 */
public interface TaskStub {
    String getId();
//    Object getJvm();  //pointer to jvm where something is running, for distributed tasks
}
