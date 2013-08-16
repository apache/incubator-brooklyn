package brooklyn.util.ssh;

import static brooklyn.util.ssh.CommonCommands.sudo;

import com.google.common.base.Optional;

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
     * Returns the command that saves on disk iptables rules, to make them resilient to reboot.
     * 
     * @return Returns the command that saves on disk iptables rules.
     */
    public static String saveIptablesRules() {
       return sudo("/sbin/service iptables save");
    }
    
    /**
     * Returns the iptables rules.
     * 
     * @return Returns the command that list all the iptables rules.
     */
    public static String listIptablesRule() {
       return sudo("/sbin/iptables -L -v -n");
    }

    /**
     * Returns the command that inserts a rule on top of the iptables' rules to all interfaces.
     * 
     * @return Returns the command that inserts a rule on top of the iptables'
     *         rules.
     */
    public static String insertIptablesRule(Chain chain, Protocol protocol, int port, Policy policy) {
        return addIptablesRule("-I", chain, Optional.<String> absent(), protocol, port, policy);
    }
    
    /**
     * Returns the command that inserts a rule on top of the iptables' rules.
     * 
     * @return Returns the command that inserts a rule on top of the iptables'
     *         rules.
     */
    public static String insertIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port,
            Policy policy) {
        return addIptablesRule("-I", chain, Optional.of(networkInterface), protocol, port, policy);
    }

    /**
     * Returns the command that appends a rule to iptables to all interfaces.
     * 
     * @return Returns the command that appends a rule to iptables.
     */
    public static String appendIptablesRule(Chain chain, Protocol protocol, int port,
            Policy policy) {
        return addIptablesRule("-A", chain, Optional.<String> absent(), protocol, port, policy);
    }
    
    /**
     * Returns the command that appends a rule to iptables.
     * 
     * @return Returns the command that appends a rule to iptables.
     */
    public static String appendIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port,
            Policy policy) {
        return addIptablesRule("-A", chain, Optional.of(networkInterface), protocol, port, policy);
    }

    /**
     * Returns the command that creates a rule to iptables.
     * 
     * @return Returns the command that creates a rule to iptables.
     */
    private static String addIptablesRule(String direction, Chain chain, Optional<String> networkInterface, Protocol protocol, int port,
            Policy policy) {
        String addIptablesRule; 
        if(networkInterface.isPresent()) {  
           addIptablesRule = String.format("/sbin/iptables %s %s -i %s -p %s --dport %d -j %s", direction, chain, networkInterface.get(), protocol, port, policy);
        } else {
           addIptablesRule = String.format("/sbin/iptables %s %s -p %s --dport %d -j %s", direction, chain,
                 protocol, port, policy);
        }
        return sudo(addIptablesRule);
    }

}
