package brooklyn.entity.basic.lifecycle;

public interface JavaStartStopDriver extends StartStopDriver {

    Integer getJmxPort();

    Integer getRmiPort();

    String getJmxContext();
}
