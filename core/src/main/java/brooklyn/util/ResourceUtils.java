package brooklyn.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.common.io.Closeables;

public class ResourceUtils {

    public static String loadResource(Class<?> clazz, String resourceName) {
        BufferedReader reader = null;
        try {
            InputStream is = clazz.getResourceAsStream(resourceName);
            reader = new BufferedReader(new InputStreamReader(is));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
            return sb.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Problem reading resource '"+resourceName+"': "+e, e);
            
        } finally {
            Closeables.closeQuietly(reader);
        }
    }

}
