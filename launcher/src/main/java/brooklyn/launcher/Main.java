package brooklyn.launcher;

import java.util.HashMap;
import java.util.Map;

import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

public class Main {
    Map<String, String> config = new HashMap<String, String>();

    public static void main(String[] args) throws Exception {
        int port = 8081;

        for (int i=0; i<args.length-1; i++) {
            if ("--httpPort".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            }
        }

        // TODO allow wiring in of other, non-local, ManagementContext
        ManagementContext context = new LocalManagementContext();
        WebAppRunner launcher =  new WebAppRunner(context, port, "/brooklyn.war");
        launcher.start();
    }
    
}
