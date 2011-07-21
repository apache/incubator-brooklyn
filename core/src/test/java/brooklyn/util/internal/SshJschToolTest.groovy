package brooklyn.util.internal

import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshJschToolTest {
    @Test(groups = [ "Integration" ])
    public void testSshTool() {
        def t = new SshJschTool(host:'localhost')
        t.connect()

        t.createFile "/tmp/say-hello.sh", "echo hello world!\n"
        t.execCommands([ "mkdir -p /tmp/exec" ])
        t.copyToServer permissions:'0755', new File("/tmp/say-hello.sh"), "/tmp/exec/"
        t.execCommands(out:System.out, [
            "cat /tmp/exec/say-hello.sh",
            "ls -al /tmp/exec/say-hello.sh",
            "/tmp/exec/say-hello.sh",
            "exit"
        ])

        t.disconnect()
    }

    @Test(groups = [ "WIP" ])
    public void testAwsSsh() {
        def t = new SshJschTool(user:"root", host:"ec2-50-17-6-2.compute-1.amazonaws.com", sshPublicKey:"/Users/adk/.ssh/id_rsa.pub", sshPrivateKey:"/Users/adk/.ssh/id_rsa" )
        t.connect()
        t.execCommands([ "date", "hostname" ])
        t.disconnect()
    }
}
