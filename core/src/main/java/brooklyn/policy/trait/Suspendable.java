package brooklyn.policy.trait;

public interface Suspendable {
    /**
     * Suspend the policy.
     */
    void suspend();

    /**
     * Resume the policy.
     */
    void resume();

}
