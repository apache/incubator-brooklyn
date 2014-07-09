package brooklyn.demo.webapp.hello;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

public class DemoUtils {


    public static String stringFromInputStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line).append("\n");
        return sb.toString();
    }
    
    public static String getStackTrace(Throwable t) {
        StringWriter s = new StringWriter();
        PrintWriter pw = new PrintWriter(s);
        t.printStackTrace(pw);
        pw.flush();
        return s.getBuffer().toString();
    }
    
}
