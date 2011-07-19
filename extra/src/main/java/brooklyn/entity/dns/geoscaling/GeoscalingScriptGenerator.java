package brooklyn.entity.dns.geoscaling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import brooklyn.entity.dns.ServerGeoInfo;

class GeoscalingScriptGenerator {
    
    private static final String PHP_SCRIPT_TEMPLATE_RESOURCE = "/brooklyn/entity/dns/geoscaling/template.php";
    private static final String SERVER_DECLARATIONS_MARKER = "/* SERVER DECLARATIONS TO BE SUBSTITUTED HERE */";
    private static final String DATESTAMP_MARKER = "DATESTAMP";

    
    public static String generateScriptString(Set<ServerGeoInfo> servers) {
        String template = loadResource(PHP_SCRIPT_TEMPLATE_RESOURCE);
        String datestamp = new SimpleDateFormat("E, dd MMM yyyy 'at' HH:mm:ss Z").format(new Date());
        String declarations = getServerDeclaration(servers);
        return template
            .replace(DATESTAMP_MARKER, datestamp)
            .replace(SERVER_DECLARATIONS_MARKER, declarations);
    }
    
    private static String getServerDeclaration(Set<ServerGeoInfo> servers) {
        StringBuffer sb = new StringBuffer();
        sb.append("$servers = array(\n");
        Iterator<ServerGeoInfo> iServer = servers.iterator();
        while (iServer.hasNext()) {
            ServerGeoInfo server = iServer.next();
            sb.append("    array('name'      => '").append(server.displayName).append("',\n");
            sb.append("          'latitude'  => ").append(server.latitude).append(",\n");
            sb.append("          'longitude' => ").append(server.longitude).append(",\n");
            sb.append("          'ip'        => '").append(server.address).append("')");
            if (iServer.hasNext()) sb.append(",");
            sb.append("\n");
        }
        sb.append(");\n");
        return sb.toString();
    }
    
    // TODO: move to sensible "general utilities" location
    private static String loadResource(String resourceName) {
        BufferedReader reader = null;
        try {
            InputStream is = GeoscalingScriptGenerator.class.getResourceAsStream(resourceName);
            reader = new BufferedReader(new InputStreamReader(is));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
            return sb.toString();
            
        } catch (IOException e) {
            throw new RuntimeException("Problem reading resource '"+resourceName+"': "+e, e);
            
        } finally {
            try {
                if (reader != null)
                    reader.close();
                
            } catch (IOException e) {
                // Um, ignore.
            }
        }
    }
    
}
