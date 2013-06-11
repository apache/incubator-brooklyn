package brooklyn.entity.dns.geoscaling;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.ResourceUtils;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Strings;

class GeoscalingScriptGenerator {
    
    private static final String PHP_SCRIPT_TEMPLATE_RESOURCE = JavaClassNames.resolveClasspathUrl(GeoscalingScriptGenerator.class, "template.php");
    private static final String HOSTS_DECLARATIONS_MARKER = "/* HOST DECLARATIONS TO BE SUBSTITUTED HERE */";
    private static final String DATESTAMP_MARKER = "DATESTAMP";

    
    public static String generateScriptString(Collection<HostGeoInfo> hosts) {
        return generateScriptString(new Date(), hosts);
    }
    
    public static String generateScriptString(Date generationTime, Collection<HostGeoInfo> hosts) {
        String template = new ResourceUtils(GeoscalingScriptGenerator.class).getResourceAsString(PHP_SCRIPT_TEMPLATE_RESOURCE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String datestamp = sdf.format(generationTime);
        String declarations = getHostsDeclaration(hosts);
        return template
            .replaceAll(DATESTAMP_MARKER, datestamp)
            .replace(HOSTS_DECLARATIONS_MARKER, declarations);
    }
    
    private static String getHostsDeclaration(Collection<HostGeoInfo> hosts) {
        StringBuffer sb = new StringBuffer();
        sb.append("$hosts = array(\n");
        Iterator<HostGeoInfo> iServer = hosts.iterator();
        while (iServer.hasNext()) {
            HostGeoInfo server = iServer.next();
            sb.append("    array('name'      => '").append(escape(server.displayName)).append("',\n");
            sb.append("          'latitude'  => ").append(server.latitude).append(",\n");
            sb.append("          'longitude' => ").append(server.longitude).append(",\n");
            sb.append("          'ip'        => '").append(escape(server.address)).append("')");
            if (iServer.hasNext()) sb.append(",\n");
            sb.append("\n");
        }
        sb.append(");\n");
        return sb.toString();
    }
    
    private static String escape(String txt) {
        txt = Strings.replaceAllNonRegex(txt, "\\", "\\\\");
        txt = Strings.replaceAllNonRegex(txt, "'", "\\'");
        txt = Strings.replaceAllNonRegex(txt, "\"", "\\\"'");
        return txt;
    }
    
}
