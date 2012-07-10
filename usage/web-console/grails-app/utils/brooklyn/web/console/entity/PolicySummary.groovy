package brooklyn.web.console.entity;

import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy

/** Summary of a Brookln Entity Policy   */
public class PolicySummary {
    final String displayName
    final String policyStatus
    final String id

    public PolicySummary(Policy policy) {
        id = policy.id
        displayName = policy.name
        if (policy.isDestroyed()) {
            policyStatus = "Destroyed"
        } else if (policy.isSuspended()) {
            policyStatus = "Suspended"
        } else {
            policyStatus = "Running"
        }
    }

}


