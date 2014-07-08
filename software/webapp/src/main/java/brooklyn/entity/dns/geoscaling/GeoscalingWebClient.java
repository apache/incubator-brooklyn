/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.dns.geoscaling;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;

import brooklyn.util.text.Strings;

public class GeoscalingWebClient {
    public static final Logger log = LoggerFactory.getLogger(GeoscalingWebClient.class);
    
    public static final long PROVIDE_NETWORK_INFO = 1 << 0;
    public static final long PROVIDE_CITY_INFO    = 1 << 1;
    public static final long PROVIDE_COUNTRY_INFO = 1 << 2;
    public static final long PROVIDE_EXTRA_INFO   = 1 << 3;
    public static final long PROVIDE_UPTIME_INFO  = 1 << 4;
    
    private static final String HOST ="www.geoscaling.com";
    private static final String PATH ="dns2/index.php";
    private DefaultHttpClient httpClient;
    private Tidy tidy;
    private List<Domain> primaryDomains = null;
    
    
    public class Domain {
        public final int id;
        public final String name;
        private List<SmartSubdomain> smartSubdomains = null;
        
        public Domain(int id, String name) {
            this.id = id;
            this.name = name.toLowerCase();
        }
        
        public List<SmartSubdomain> getSmartSubdomains() {
            if (smartSubdomains == null)
                smartSubdomains = GeoscalingWebClient.this.fetchSmartSubdomains(this);
            return smartSubdomains;
        }
        
        public SmartSubdomain getSmartSubdomain(String name) {
            name = name.toLowerCase();
            for (SmartSubdomain s : getSmartSubdomains()) {
                if (s.name.equals(name)) return s;
            }
            return null;
        }
        
        /** e.g. editRecord("foo", "A", "1.2.3.4"), which assuming this domain is "bar.com", will create A record for foo.bar.com.
         * <p>
         * or editRecord("*.foo", "CNAME", "foo.bar.com") to map everything at *.foo.bar.com to foo.bar.com
         */
        public void editRecord(String subdomainPart, String type, String content) {
            subdomainPart = Strings.removeFromEnd(subdomainPart, "."+name);
            editSubdomainRecord(id, subdomainPart, type, content);
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
            this.name = name.toLowerCase();
        }
        
        public void configure(long flags, String phpScript) {
            configureSmartSubdomain(parent.id, id, name, flags, phpScript);
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
        this.httpClient = new DefaultHttpClient();
        this.tidy = new Tidy();
        // Silently swallow all HTML errors/warnings.
        tidy.setErrout(new PrintWriter(new OutputStream() {
            @Override public void write(int b) throws IOException { }
        }));
    }
    
    public GeoscalingWebClient(String username, String password) {
        this();
        login(username, password);
    }
    
    public void login(String username, String password) {
        try {
            String url = MessageFormat.format("https://{0}/{1}?module=auth", HOST, PATH);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("username", username));
            nameValuePairs.add(new BasicNameValuePair("password", password));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            sendRequest(request, true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to log-in to GeoScaling service: "+e, e);
        }
    }
    
    public void logout() {
        try {
            String url = MessageFormat.format("https://{0}/{1}?module=auth&logout", HOST, PATH);
            sendRequest(new HttpGet(url), true);
            
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
        name = name.toLowerCase();
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
            name = name.toLowerCase();
            String url = MessageFormat.format("https://{0}/{1}?module=domains", HOST, PATH);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("domain", name));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            sendRequest(request, true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GeoScaling smart subdomain: "+e, e);
        }
        
        primaryDomains = fetchPrimaryDomains();
    }
    
    private List<Domain> fetchPrimaryDomains() {
        try {
            List<Domain> domains = new LinkedList<Domain>();
            String url = MessageFormat.format("https://{0}/{1}?module=domains", HOST, PATH);
            HttpResponse response = sendRequest(new HttpGet(url), false);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
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
                    "https://{0}/{1}?module=domain&id={2,number,#}&delete=1",
                    HOST, PATH, primaryDomainId);
            
            sendRequest(new HttpGet(url), true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete GeoScaling primary domain: "+e, e);
        }
    }
    
    private List<SmartSubdomain> fetchSmartSubdomains(Domain parent) {
        try {
            List<SmartSubdomain> subdomains = new LinkedList<SmartSubdomain>();
            
            String url = MessageFormat.format(
                    "https://{0}/{1}?module=smart_subdomains&id={2,number,#}",
                    HOST, PATH, parent.id);
            
            HttpResponse response = sendRequest(new HttpGet(url), false);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
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
            smartSubdomainName = smartSubdomainName.toLowerCase();
            String url = MessageFormat.format(
                    "https://{0}/{1}?module=smart_subdomains&id={2,number,#}",
                    HOST, PATH, primaryDomainId);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("smart_subdomain_name", smartSubdomainName));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                        
            sendRequest(request, true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GeoScaling smart subdomain: "+e, e);
        }
    }
    
    private void deleteSmartSubdomain(int primaryDomainId, int smartSubdomainId) {
        try {
            String url = MessageFormat.format(
                    "https://{0}/{1}?module=smart_subdomains&id={2,number,#}&delete={3,number,#}",
                    HOST, PATH, primaryDomainId, smartSubdomainId);
            
            sendRequest(new HttpGet(url), true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete GeoScaling smart subdomain: "+e, e);
        }
    }
    
    private void configureSmartSubdomain(int primaryDomainId, int smartSubdomainId, String smartSubdomainName,
            long flags, String phpScript) {
        
        try {
            smartSubdomainName = smartSubdomainName.toLowerCase();
            String url = MessageFormat.format(
                    "https://{0}/{1}?module=smart_subdomain&id={2,number,#}&subdomain_id={3,number,#}",
                    HOST, PATH, primaryDomainId, smartSubdomainId);
            
            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("MAX_FILE_SIZE", "65536"));
            nameValuePairs.add(new BasicNameValuePair("name", smartSubdomainName));
            if ((flags & PROVIDE_NETWORK_INFO) != 0) nameValuePairs.add(new BasicNameValuePair("share_as_info", "on"));
            if ((flags & PROVIDE_CITY_INFO) != 0) nameValuePairs.add(new BasicNameValuePair("share_city_info", "on"));
            if ((flags & PROVIDE_COUNTRY_INFO) != 0) nameValuePairs.add(new BasicNameValuePair("share_country_info", "on"));
            if ((flags & PROVIDE_EXTRA_INFO) != 0) nameValuePairs.add(new BasicNameValuePair("share_extra_info", "on"));
            if ((flags & PROVIDE_UPTIME_INFO) != 0) nameValuePairs.add(new BasicNameValuePair("share_uptime_info", "on"));
            nameValuePairs.add(new BasicNameValuePair("code", phpScript));
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            sendRequest(request, true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update GeoScaling smart subdomain: "+e, e);
        }
    }

    private void editSubdomainRecord(int primaryDomainId, String record, String type, String content) {
        
        try {
            String url = MessageFormat.format(
                    "https://{0}/{1}?",
                    HOST, "dns2/ajax/add_record.php");

            HttpPost request = new HttpPost(url);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("id", ""+primaryDomainId));
            nameValuePairs.add(new BasicNameValuePair("name", record));
            nameValuePairs.add(new BasicNameValuePair("type", type));
            nameValuePairs.add(new BasicNameValuePair("content", content));
            nameValuePairs.add(new BasicNameValuePair("ttl", "300"));
            nameValuePairs.add(new BasicNameValuePair("prio", "0"));
           
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            
            sendRequest(request, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update GeoScaling smart subdomain: "+e, e);
        }
    }
    

    protected HttpResponse sendRequest(HttpUriRequest request, boolean consumeResponse) throws ClientProtocolException, IOException {
        if (log.isDebugEnabled()) log.debug("Geoscaling request: "+
                request.getURI()+
                (request instanceof HttpPost ? " "+((HttpPost)request).getEntity() : ""));
        HttpResponse response = httpClient.execute(request);
        if (log.isDebugEnabled()) log.debug("Geoscaling response: "+response);
        if (consumeResponse)
            EntityUtils.consume(response.getEntity());
        return response;
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
