package brooklyn.launcher

import brooklyn.management.internal.LocalManagementContext
import org.testng.annotations.Test
import static org.testng.Assert.assertNotNull

class WebAppRunnerTest {

    @Test
    public void ping() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), 8090,"/web-console.war");
        assertNotNull(launcher);

        launcher.start();

        for (int i=0; i<50; i++) {
            try {
                Thread.sleep(500)
                new URL("http://localhost:8090/").getContent();
                break;
            } catch (IOException e) {
                ;
            }
        }

        assertNotNull(new URL("http://localhost:8090/").getContent());
        launcher.stop();
    }

}
