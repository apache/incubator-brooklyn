package brooklyn.entity.basic.lifecycle;

public interface JavaSoftwareProcessDriver extends SoftwareProcessDriver {

    Integer getJmxPort();

    Integer getRmiPort();

    String getJmxContext();
}
