package brooklyn.entity.dns.geoscaling;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

import brooklyn.entity.dns.HostGeoInfo;
import brooklyn.util.ResourceUtils;

class GeoscalingScriptGenerator {
    
    private static final String PHP_SCRIPT_TEMPLATE_RESOURCE = "/brooklyn/entity/dns/geoscaling/template.php";
    private static final String HOSTS_DECLARATIONS_MARKER = "/* HOST DECLARATIONS TO BE SUBSTITUTED HERE */";
    private static final String DATESTAMP_MARKER = "DATESTAMP";

    
    public static String generateScriptString(Set<HostGeoInfo> hosts) {
        return generateScriptString(new Date(), hosts);
    }
    
    public static String generateScriptString(Date generationTime, Set<HostGeoInfo> hosts) {
        String template = ResourceUtils.loadResource(GeoscalingScriptGenerator.class, PHP_SCRIPT_TEMPLATE_RESOURCE);
        SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyyy 'at' HH:mm:ss 'UTC'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String datestamp = sdf.format(generationTime);
        String declarations = getHostsDeclaration(hosts);
        return template
            .replaceAll(DATESTAMP_MARKER, datestamp)
            .replace(HOSTS_DECLARATIONS_MARKER, declarations);
    }
    
    private static String getHostsDeclaration(Set<HostGeoInfo> hosts) {
        StringBuffer sb = new StringBuffer();
        sb.append("$hosts = array(\n");
        Iterator<HostGeoInfo> iServer = hosts.iterator();
        while (iServer.hasNext()) {
            HostGeoInfo server = iServer.next();
            sb.append("    array('name'      => '").append(server.displayName).append("',\n");
            sb.append("          'latitude'  => ").append(server.latitude).append(",\n");
            sb.append("          'longitude' => ").append(server.longitude).append(",\n");
            sb.append("          'ip'        => '").append(server.address).append("')");
            if (iServer.hasNext()) sb.append(",\n");
            sb.append("\n");
        }
        sb.append(");\n");
        return sb.toString();
    }
    
}
