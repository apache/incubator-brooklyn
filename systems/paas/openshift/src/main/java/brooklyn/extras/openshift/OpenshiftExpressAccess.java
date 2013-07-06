package brooklyn.extras.openshift;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import groovyx.net.http.HTTPBuilder;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Maps;

class OpenshiftExpressAccess {

    private static final Logger log = LoggerFactory.getLogger(OpenshiftExpressAccess.class);
    
    @SetFromFlag(defaultVal="https://openshift.redhat.com/broker", nullable=false)
    String urlBase;
    
    @SetFromFlag(nullable=false)
    String username;
    
    @SetFromFlag(nullable=false)
    String password;

    OpenshiftExpressAccess(Map flags) {
        ConfigBag bag = new ConfigBag().putAll(flags);
        FlagUtils.setFieldsFromFlags(this, bag, true);
    }
    
    protected void validate() {
        FlagUtils.checkRequiredFields(this);
    }

    public Object doPost(String urlExtension) {
        return doPost(MutableMap.of(), urlExtension);
    }

    public Object doPost(Map jsonDataFields) {
        return doPost(jsonDataFields, "/cartridge");
    }
    
    public Object doPost(Map jsonDataFields, String urlExtension) {
        validate();

        try {
            HTTPBuilder http = new HTTPBuilder( urlBase + urlExtension );
            // FIXME Port this to Java when needed
    //        http.handler.failure = { resp ->
    //            log.warn "Unexpected failure: ${resp.statusLine} ${resp.status} ${resp.data} ${resp.responseData}"
    //            resp.headers.each { log.info "  header ${it}" }
    //            log.warn "Details: "+resp
    //            throw new IllegalStateException("server could not process request ("+resp.statusLine+")")
    //        }
    
            JsonBuilder jsonBuilder = new JsonBuilder();
            Map allFields = Maps.newLinkedHashMap();
            allFields.put("rhlogin", username);
            allFields.putAll(jsonDataFields);
            jsonBuilder.call(allFields);
            String jsonData = jsonBuilder.toString();
    
            log.info("posting to "+urlBase+urlExtension+" : "+jsonData);
    
            Object result;
            // FIXME Port this to Java when needed
    //        Object o = http.post( query: [json_data: jsonData, password:"${password}"]) { resp, json ->
    //            if (resp.status==200) result = json;
    //            else throw new IllegalStateException("OpenShift server responded: "+resp)
    //        }
            //FIXME is o result?
            //return result;
            
            throw new UnsupportedOperationException(); // TODO Convert method to proper Java!
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public OpenshiftJsonResult<UserInfoResult> getUserInfo() {
        return new OpenshiftJsonResult<UserInfoResult>(doPost("/userinfo"), UserInfoResult.class); 
    }


    public enum Cartridge {
        JBOSS_AS_7("jbossas-7.0");

        final String name;
        private Cartridge(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }


    static class OpenshiftJsonResult<T> {
        /** typically contains 'data', 'message', 'debug'; as well as e.g. 'user_info' etc */
        Object raw;
        Object dataUntyped;
        T dataTyped = null;
        Class<T> type;

        // FIXME What type is raw supposed to be, to be able to call raw.data etc? 
        public OpenshiftJsonResult(Object raw, Class<T> type) {
            this.raw = raw;
            this.type = type;
            this.dataUntyped = new JsonSlurper().parseText(null);// FIXME raw.data);
            this.dataTyped = TypeCoercions.coerce(dataUntyped, type);
        }

        /** returns typed value */
        public T getData() {
            return dataTyped;
        }

        public String getMessages() {
            return null; // FIXME raw.messages;
        }

        public String getApi() {
            return null; // FIXME raw.api;
        }

        @Override
        public String toString() {
            return type.getSimpleName()+":"+raw;
        }
        /*
         * [debug:, 
         * data:{"user_info":{"rhc_domain":"rhcloud.com","rhlogin":"alex.heneveld@cloudsoftcorp.com","uuid":"daf856d5728a422bb2dcf49d474cfa2f","ssh_key":"AAAAB3NzaC1yc2EAAAABIwAAAQEA8VrGR0439k6TiNz/mby+sDV4KBp9yv4s+pv/M1gknxGfMNxsIzi6bdrGPtCS4NKrTImGqeK0xUFa98WhVS0gHbdX8ebi+RxfOYM5w7NOLlzVzOrETh+PIIHepOFTDCGi8qlly2v6eYq2jYLVOyVakFAp29/qG9QhRFGQvdknXq2JGP0+EB2kBeIJY4V9AesJk9dTgTrJALxs1p32tU44+IP4Tbfsn73LiHuaRZCI/B03Ug4qlRyhvXG8sl9VAaGCVTWU9qn4zZWzrGG6XUWss4UG7v5isiRUcz3onizD9sgEfgo+XoitCzTX30nQZYNVt1gbGGT+Pn+q49hM04jUHQ==","namespace":"alex1"},"app_info":{"alexapp1":{"uuid":"5bd3e7cfd5d142e29bc4f117498d8f4c","creation_time":"2011-10-11T16:33:44-04:00","framework":"jbossas-7.0","embedded":null},"alexapp2":{"uuid":"82c321a786d3423c9aad56cd1b462f99","creation_time":"2011-10-11T16:43:42-04:00","framework":"jbossas-7.0","embedded":null}}}, 
         * broker:1.1.1, 
         * broker_c:[namespace, rhlogin, ssh, app_uuid, debug, alter, cartridge, cart_type, action, app_name, api], 
         * messages:, 
         * result:null, 
         * api:1.1.1, 
         * api_c:[placeholder], 
         * exit_code:0]
         */
    }

    static class UserInfoResult {
        Map<String,AppInfoFields> app_info;
    }
    
    static class UserInfoFields {
        String rhc_domain;
        String rhlogin;
        String uuid;
        String ssh_key;
        String namespace;
    }
    UserInfoFields user_info;

    static class AppInfoFields {
        String uuid;
        String creation_time;
        String framework;
        String embedded;
    }
}
