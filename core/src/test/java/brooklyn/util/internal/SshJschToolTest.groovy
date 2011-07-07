package brooklyn.util.internal

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
}
