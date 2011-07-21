package brooklyn.entity.dns.geoscaling;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;

public class GeoscalingWebClient {
    public static final String DEFAULT_PROTOCOL ="http";
    public static final String DEFAULT_HOST ="www.geoscaling.com";
    public static final int DEFAULT_PORT = 80;
    private static final String PATH ="dns2/index.php";
    
    private final String protocol;
    private final String host;
    private final int port;
    private DefaultHttpClient httpClient;
    
    private List<Domain> primaryDomains = null;
    
    
    public class Domain {
        public final int id;
        public final String name;
        private List<SmartSubdomain> smartSubdomains = null;
        
        public Domain(int id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public List<SmartSubdomain> getSmartSubdomains() {
            if (smartSubdomains == null)
                smartSubdomains = GeoscalingWebClient.this.fetchSmartSubdomains(this);
            return smartSubdomains;
        }
        
        public SmartSubdomain getSmartSubdomain(String name) {
            for (SmartSubdomain s : getSmartSubdomains()) {
                if (s.name.equals(name)) return s;
            }
            return null;
        }
        
        public SmartSubdomain getSmartSubdomain(int id) {
            for (SmartSubdomain s : getSmartSubdomains()) {
                if (s.id == id) return s;
            }
            return null;
        }
        
        public void createSmartSubdomain(String name) {
            GeoscalingWebClient.this.createSmartSubdomain(id, name);
            smartSubdomains = fetchSmartSubdomains(this);
        }
        
        public void delete() {
            deletePrimaryDomain(id);
            primaryDomains = fetchPrimaryDomains();
        }
        
        @Override
        public String toString() {
            return "Domain["+name+" ("+id+")]";
        }
        
        @Override
        public int hashCode() {
            return id;
        }
    }
    
    
    public class SmartSubdomain {
        public final Domain parent;
        public final int id;
        public String name;
        
        public SmartSubdomain(Domain parent, int id, String name) {
            this.parent = parent;
            this.id = id;
            this.name = name;
        }
        
        public void configure(boolean shareNetworkInfo, boolean shareCityInfo, boolean shareCountryName,
                boolean shareExtraInfo, boolean shareUptimeInfo, String phpScript) {
            
            configureSmartSubdomain(
                    parent.id, id, name,
                    shareNetworkInfo, shareCityInfo, shareCountryName, shareExtraInfo, shareUptimeInfo, phpScript);
        }
        
        public void delete() {
            deleteSmartSubdomain(parent.id, id);
            parent.smartSubdomains = fetchSmartSubdomains(parent);
        }
        
        @Override
        public String toString() {
            return "SmartSubdomain["+name+" ("+id+")]";
        }
        
        @Override
        public int hashCode() {
            return id;
        }
    }
    
    
    public GeoscalingWebClient() {
        this(DEFAULT_PROTOCOL, DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public GeoscalingWebClient(String protocol, String host, int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.httpClient = new DefaultHttpClient();
    }
    
    public void login(String username, String password) {
        try {
            String url = MessageFormat.format("{0}://{1}:{2}/{3}?module=auth", protocol, host, port, PATH);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("username", username));
            nameValuePairs.add(new BasicNameValuePair("password", password));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            HttpResponse response = httpClient.execute(request);
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to log-in to GeoScaling service: "+e, e);
        }
    }
    
    public void logout() {
        try {
            String url = MessageFormat.format("{0}://{1}:{2}/{3}?module=auth&logout", protocol, host, port, PATH);
            HttpResponse response = httpClient.execute(new HttpGet(url));
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to log-out of GeoScaling service: "+e, e);
        }
    }
    
    public List<Domain> getPrimaryDomains() {
        if (primaryDomains == null)
            primaryDomains = fetchPrimaryDomains();
        return primaryDomains;
    }
    
    public Domain getPrimaryDomain(String name) {
        for (Domain d : getPrimaryDomains()) {
            if (d.name.equals(name)) return d;
        }
        return null;
    }
    
    public Domain getPrimaryDomain(int id) {
        for (Domain d : getPrimaryDomains()) {
            if (d.id == id) return d;
        }
        return null;
    }
    
    public void createPrimaryDomain(String name) {
        try {
            String url = MessageFormat.format("{0}://{1}:{2}/{3}?module=domains", protocol, host, port, PATH);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("domain", name));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            HttpResponse response = httpClient.execute(request);
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GeoScaling smart subdomain: "+e, e);
        }
        
        primaryDomains = fetchPrimaryDomains();
    }
    
    private List<Domain> fetchPrimaryDomains() {
        try {
            List<Domain> domains = new LinkedList<Domain>();
            String url = MessageFormat.format("{0}://{1}:{2}/{3}?module=domains", protocol, host, port, PATH);
            
            HttpResponse response = httpClient.execute(new HttpGet(url));
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Tidy tidy = new Tidy();
                Document document = tidy.parseDOM(entity.getContent(), null);
                NodeList links = document.getElementsByTagName("a");
                for (int i = 0; i < links.getLength(); ++i) {
                    Element link = (Element) links.item(i);
                    String href = link.getAttribute("href");
                    Pattern p = Pattern.compile("module=domain.*&id=(\\d+)");
                    Matcher m = p.matcher(href);
                    if (!m.find(0)) continue;
                    
                    int id = Integer.parseInt(m.group(1));
                    String name = getTextContent(link).trim();
                    if (name.length() == 0) continue;
                    
                    domains.add(new Domain(id, name));
                }
                
                EntityUtils.consume(entity);
            }
            
            return domains;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve GeoScaling subdomains: "+e, e);
        }
    }
    
    private void deletePrimaryDomain(int primaryDomainId) {
        try {
            String url = MessageFormat.format(
                    "{0}://{1}:{2}/{3}?module=domain&id={4,number,#}&delete=1",
                    protocol, host, port, PATH, primaryDomainId);
            
            HttpResponse response = httpClient.execute(new HttpGet(url));
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete GeoScaling primary domain: "+e, e);
        }
    }
    
    private List<SmartSubdomain> fetchSmartSubdomains(Domain parent) {
        try {
            List<SmartSubdomain> subdomains = new LinkedList<SmartSubdomain>();
            
            String url = MessageFormat.format(
                    "{0}://{1}:{2}/{3}?module=smart_subdomains&id={4,number,#}",
                    protocol, host, port, PATH, parent.id);
            
            HttpResponse response = httpClient.execute(new HttpGet(url));
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Tidy tidy = new Tidy();
                Document document = tidy.parseDOM(entity.getContent(), null);
                NodeList links = document.getElementsByTagName("a");
                for (int i = 0; i < links.getLength(); ++i) {
                    Element link = (Element) links.item(i);
                    String href = link.getAttribute("href");
                    Pattern p = Pattern.compile("module=smart_subdomain.*&subdomain_id=(\\d+)");
                    Matcher m = p.matcher(href);
                    if (!m.find(0)) continue;
                    
                    int subdomainId = Integer.parseInt(m.group(1));
                    String name = getTextContent(link);
                    if (name.trim().length() == 0) continue;
                    
                    name = name.substring(0, name.length() - parent.name.length() - 1);
                    subdomains.add(new SmartSubdomain(parent, subdomainId, name));
                }
                
                EntityUtils.consume(entity);
            }
            
            return subdomains;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve GeoScaling smart subdomains: "+e, e);
        }
    }

    private void createSmartSubdomain(int primaryDomainId, String smartSubdomainName) {
        try {
            String url = MessageFormat.format(
                    "{0}://{1}:{2}/{3}?module=smart_subdomains&id={4,number,#}",
                    protocol, host, port, PATH, primaryDomainId);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("smart_subdomain_name", smartSubdomainName));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            HttpResponse response = httpClient.execute(request);
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GeoScaling smart subdomain: "+e, e);
        }
    }
    
    private void deleteSmartSubdomain(int primaryDomainId, int smartSubdomainId) {
        try {
            String url = MessageFormat.format(
                    "{0}://{1}:{2}/{3}?module=smart_subdomains&id={4,number,#}&delete={5,number,#}",
                    protocol, host, port, PATH, primaryDomainId, smartSubdomainId);
            
            HttpResponse response = httpClient.execute(new HttpGet(url));
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete GeoScaling smart subdomain: "+e, e);
        }
    }
    
    private void configureSmartSubdomain(int primaryDomainId, int smartSubdomainId,
            String smartSubdomainName, boolean shareNetworkInfo, boolean shareCityInfo,
            boolean shareCountryName, boolean shareExtraInfo, boolean shareUptimeInfo,
            String phpScript) {
        
        try {
            String url = MessageFormat.format(
                    "{0}://{1}:{2}/{3}?module=smart_subdomain&id={4,number,#}&subdomain_id={5,number,#}",
                    protocol, host, port, PATH, primaryDomainId, smartSubdomainId);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("name", smartSubdomainName));
            if (shareNetworkInfo) nameValuePairs.add(new BasicNameValuePair("share_as_info", "on"));
            if (shareCountryName) nameValuePairs.add(new BasicNameValuePair("share_country_info", "on"));
            if (shareCityInfo) nameValuePairs.add(new BasicNameValuePair("share_city_info", "on"));
            if (shareExtraInfo) nameValuePairs.add(new BasicNameValuePair("share_extra_info", "on"));
            if (shareUptimeInfo) nameValuePairs.add(new BasicNameValuePair("share_uptime_info", "on"));
            nameValuePairs.add(new BasicNameValuePair("code", phpScript));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            HttpResponse response = httpClient.execute(request);
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update GeoScaling smart subdomain: "+e, e);
        }
    }
    
    private static String getTextContent(Node n) {
        StringBuffer sb = new StringBuffer();
        NodeList childNodes = n.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); ++i) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE)
                sb.append(child.getNodeValue());
            else
                sb.append(getTextContent(child));
        }
        return sb.toString();
    }
    
}
