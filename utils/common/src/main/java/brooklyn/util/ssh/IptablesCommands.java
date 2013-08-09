package brooklyn.util.ssh;

import static brooklyn.util.ssh.CommonCommands.chain;
import static brooklyn.util.ssh.CommonCommands.sudo;

import com.google.common.collect.ImmutableList;

public class IptablesCommands {

    public enum Chain {
        INPUT, FORWARD, OUTPUT
    }

    public enum Policy {
        ACCEPT, REJECT, DROP, LOG
    }

    public enum Protocol {
        TCP("tcp"), UDP("udp"), ALL("all");

        final String protocol;

        private Protocol(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return protocol;
        }
    }

    /**
     * Returns the command that cleans up iptables rules.
     * 
     * @return Returns the command that cleans up iptables rules.
     */
    public static String cleanUpIptablesRules() {
       return sudo("/sbin/iptables -F");
    }

    /**
     * Returns the command that inserts a rule on top of the iptables' rules.
     * 
     * @return Returns the command that inserts a rule on top of the iptables'
     *         rules.
     */
    public static String insertIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port,
            Policy policy) {
        return addIptablesRule("-I", chain, networkInterface, protocol, port, policy);
    }

    /**
     * Returns the command that appends a rule to iptables.
     * 
     * @return Returns the command that appends a rule to iptables.
     */
    public static String appendIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port,
            Policy policy) {
        return addIptablesRule("-A", chain, networkInterface, protocol, port, policy);
    }

    /**
     * Returns the command that creates a rule to iptables.
     * 
     * @return Returns the command that creates a rule to iptables.
     */
    private static String addIptablesRule(String direction, Chain chain, String networkInterface, Protocol protocol,
            int port, Policy policy) {
        String addIptablesRule = String.format("/sbin/iptables %s %s -i %s -p %s --dport %d -j %s", direction, chain,
                networkInterface, protocol, port, policy);
        return sudo(addIptablesRule);
    }
}
