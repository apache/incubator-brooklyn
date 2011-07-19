package brooklyn.entity.dns.geoscaling;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

class GeoscalingWebClient {
    private static final String DEFAULT_HOST ="www.geoscaling.com";
    private static final int DEFAULT_PORT = 80;
    private static final String PATH ="dns2/index.php";
    
    private final String host;
    private final int port;
    private DefaultHttpClient httpClient;

    
    public GeoscalingWebClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public GeoscalingWebClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.httpClient = new DefaultHttpClient();
    }
    
    public void login(String username, String password) {
        try {
            String url = MessageFormat.format("http://{0}:{1}/{2}?module=auth", host, port, PATH);
            
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
            String url = MessageFormat.format("http://{0}:{1}/{2}?module=auth&logout", host, port, PATH);
            HttpResponse response = httpClient.execute(new HttpGet(url));
            if (response.getEntity() != null)
                EntityUtils.consume(response.getEntity());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to log-out of GeoScaling service: "+e, e);
        }
    }
    
    public void configureSmartSubdomain(int primaryDomainId, int smartSubdomainId,
            String smartSubdomainName, boolean shareNetworkInfo, boolean shareCityInfo,
            boolean shareCountryName, boolean shareExtraInfo, boolean shareUptimeInfo,
            String phpScript) {
        
        try {
            String url = MessageFormat.format(
                    "http://{0}:{1}/{2}?module=smart_subdomain&id={3,number,#}&subdomain_id={4,number,#}",
                    host, port, PATH, primaryDomainId, smartSubdomainId);
            
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
    
}
