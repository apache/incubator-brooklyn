package brooklyn.entity.drivers.downloads;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class FilenameProducersTest {

    @Test
    public void testInferFilename() throws Exception {
        assertEquals(FilenameProducers.inferFilename("myname.tgz"), "myname.tgz");
        assertEquals(FilenameProducers.inferFilename("a/myname.tgz"), "myname.tgz");
        assertEquals(FilenameProducers.inferFilename("acme.com/download/"), "");
    }
}
