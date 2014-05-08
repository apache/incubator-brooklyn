package brooklyn.management.ha;

public enum ManagementNodeState {
    UNINITIALISED,
    STANDBY,
    MASTER,
    FAILED,
    TERMINATED;
}
