package brooklyn.demo.webapp.hello;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The remote hadoop needs access to a jar file containing the code.
 * We jump through some maven hoops to have that JAR in our classpath,
 * then on the web server we serialize that JAR then pass the filename
 * to the JobConf which gets sent to hadoop.
 */
public class SerializeHelloWorldHadoopJar {
    
    public static final Logger LOG = LoggerFactory.getLogger(SerializeHelloWorldHadoopJar.class);
    
    private static AtomicBoolean initialized = new AtomicBoolean(true);
    private static String jarFileName;
    
    public static void init() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            try {
                URL u = SerializeHelloWorldHadoopJar.class.getClassLoader().getResource("brooklyn-example-hello-world-hadoop-jar.jar");
                if (u==null) {
                    throw new FileNotFoundException("jar not on classpath");
                } else {
                    OutputStream out = new FileOutputStream("/tmp/brooklyn-example-hello-world-hadoop-jar.jar");
                    copy(u.openStream(), out);
                    out.close();
                    jarFileName = "/tmp/brooklyn-example-hello-world-hadoop-jar.jar";
                }
            } catch (Exception e) {
                LOG.warn("Cannot copy brooklyn-example-hello-world-hadoop-jar.jar; hadoop will fail: "+e);
            }
            initialized.set(true);
        }
    }

    public static String getJarName() {
        init();
        return jarFileName;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int bytesRead = input.read(buf);
        while (bytesRead != -1) {
            output.write(buf, 0, bytesRead);
            bytesRead = input.read(buf);
        }
        output.flush();
    }
    
}
