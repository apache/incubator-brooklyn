package brooklyn.util.ssh;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.ssh.IptablesCommands.Protocol;

public class IptablesCommandsTest {

    private static final String cleanUptptablesRules = "((which iptables || " +
    		"((which apt-get && echo apt-get exists, doing update && export DEBIAN_FRONTEND=noninteractive && " +
    		"( if test \"$UID\" -eq 0; then ( apt-get update ); else sudo -E -n -S -- apt-get update; fi ) && " +
    		"( if test \"$UID\" -eq 0; then ( apt-get install -y --allow-unauthenticated iptables ); " +
    		"else sudo -E -n -S -- apt-get install -y --allow-unauthenticated iptables; fi )) || " +
    		"(which yum && ( if test \"$UID\" -eq 0; then ( yum -y --nogpgcheck install iptables ); " +
    		"else sudo -E -n -S -- yum -y --nogpgcheck install iptables; fi )) || " +
    		"(which brew && brew install iptables) || (which port && ( if test \"$UID\" -eq 0; " +
    		"then ( port install iptables ); else sudo -E -n -S -- port install iptables; fi )) || " +
    		"(echo \"WARNING: no known/successful package manager to install iptables, may fail subsequently\"))) && " +
    		"( if test \"$UID\" -eq 0; then ( iptables -F ); else sudo -E -n -S -- iptables -F; fi ))";

    public static final String insertIptablesRule = "((which iptables || " +
            "((which apt-get && echo apt-get exists, doing update && export DEBIAN_FRONTEND=noninteractive && " +
            "( if test \"$UID\" -eq 0; then ( apt-get update ); else sudo -E -n -S -- apt-get update; fi ) && " +
            "( if test \"$UID\" -eq 0; then ( apt-get install -y --allow-unauthenticated iptables ); " +
            "else sudo -E -n -S -- apt-get install -y --allow-unauthenticated iptables; fi )) || " +
            "(which yum && ( if test \"$UID\" -eq 0; then ( yum -y --nogpgcheck install iptables ); " +
            "else sudo -E -n -S -- yum -y --nogpgcheck install iptables; fi )) || " +
            "(which brew && brew install iptables) || (which port && ( if test \"$UID\" -eq 0; then " +
            "( port install iptables ); else sudo -E -n -S -- port install iptables; fi )) || " +
            "(echo \"WARNING: no known/successful package manager to install iptables, may fail subsequently\"))) && " +
            "( if test \"$UID\" -eq 0; then ( iptables -I INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT ); " +
            "else sudo -E -n -S -- iptables -I INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT; fi ))";
    public static final String appendIptablesRule = "((which iptables || " +
    		"((which apt-get && echo apt-get exists, doing update && export DEBIAN_FRONTEND=noninteractive && " +
            "( if test \"$UID\" -eq 0; then ( apt-get update ); else sudo -E -n -S -- apt-get update; fi ) && " +
            "( if test \"$UID\" -eq 0; then ( apt-get install -y --allow-unauthenticated iptables ); " +
            "else sudo -E -n -S -- apt-get install -y --allow-unauthenticated iptables; fi )) || " +
            "(which yum && ( if test \"$UID\" -eq 0; then ( yum -y --nogpgcheck install iptables ); " +
            "else sudo -E -n -S -- yum -y --nogpgcheck install iptables; fi )) || " +
            "(which brew && brew install iptables) || (which port && ( if test \"$UID\" -eq 0; then " +
            "( port install iptables ); else sudo -E -n -S -- port install iptables; fi )) || " +
            "(echo \"WARNING: no known/successful package manager to install iptables, may fail subsequently\"))) && " +
            "( if test \"$UID\" -eq 0; then ( iptables -A INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT ); " +
            "else sudo -E -n -S -- iptables -A INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT; fi ))";

    @Test
    public void testCleanUpIptablesRules() {
        Assert.assertEquals(IptablesCommands.cleanUpIptablesRules(), cleanUptptablesRules);
    }

    @Test
    public void testInsertIptablesRules() {
        Assert.assertEquals(
                IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, 3306, Policy.ACCEPT),
                insertIptablesRule);
    }

    @Test
    public void testAppendIptablesRules() {
        Assert.assertEquals(
                IptablesCommands.appendIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, 3306, Policy.ACCEPT),
                appendIptablesRule);
    }
}
