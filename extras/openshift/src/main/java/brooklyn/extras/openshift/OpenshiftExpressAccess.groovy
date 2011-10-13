package brooklyn.extras.openshift

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import groovyx.net.http.HTTPBuilder
import java.util.concurrent.Future

import brooklyn.util.internal.LanguageUtils;
import brooklyn.util.internal.duplicates.ExecUtils;

class OpenshiftExpressAccess {

    String urlBase = "https://openshift.redhat.com/broker"
    String username;
    String password;

    protected def requiredFields = [];
    { requiredFields += ["urlBase", "username", "password"] }

    protected validate() {
        requiredFields.each {
            if (this."${it}"==null) throw new NullPointerException("Flag '${it}' must be passed to constructor for "+this.getClass().getSimpleName());
        }
    }

    public Object doPost(String urlExtension) { doPost([:], urlExtension) }
    public Object doPost(Map jsonDataFields=[:], String urlExtension="/cartridge") {
        validate();

        def http = new HTTPBuilder( urlBase + urlExtension )
        http.handler.failure = { resp ->
            println "Unexpected failure: ${resp.statusLine} ${resp.status} ${resp.data} ${resp.responseData}"
            resp.headers.each { println "  header ${it}" }
            println "Details: "+resp
            throw new IllegalStateException("server could not process request ("+resp.statusLine+")")
        }

        def jsonBuilder = new JsonBuilder();
        Map allFields = [:]
        allFields.rhlogin = username;
        allFields << jsonDataFields
        jsonBuilder allFields;
        String jsonData = jsonBuilder.toString()

        println "posting to "+urlBase+urlExtension+" : "+jsonData

        Object result;
        Object o = http.post( query: [json_data: jsonData, password:"${password}"]) { resp, json ->
            if (resp.status==200) result = json;
            else throw new IllegalStateException("OpenShift server responded: "+resp)
        }
        //FIXME is o result?
        return result
    }

    public OpenshiftJsonResult<UserInfoResult> getUserInfo() {
        [ doPost("/userinfo"), UserInfoResult ] as OpenshiftJsonResult<UserInfoResult>
    }


    public enum Cartridge {
        JBOSS_AS_7("jbossas-7.0");

        String name;
        public Cartridge(String name) {
            this.name = name;
        }

        public String toString() { name }
    }


    static class OpenshiftJsonResult<T> {
        /** typically contains 'data', 'message', 'debug'; as well as e.g. 'user_info' etc */
        def raw;
        def dataUntyped;
        def dataTyped = null;
        Class<T> type;

        public OpenshiftJsonResult(Object raw, Class<T> type) {
            this.raw = raw;
            this.type = type;
            this.dataUntyped = new JsonSlurper().parseText(raw.data)
            this.dataTyped = dataUntyped.asType(type)
        }

        /** returns typed value */
        public T getData() {
            return dataTyped;
        }

        public String getMessages() {
            return raw.messages;
        }

        public String getApi() {
            return raw.api;
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
        Map<String,AppInfoFields> app_info;

    }

    public static class OpenshiftExpressApplicationAccess extends OpenshiftExpressAccess {

        String cartridge = Cartridge.JBOSS_AS_7;
        String appName;
        boolean debug = false;

        { requiredFields += ["cartridge", "appName"] }

        protected validate() {
            super.validate()
            if (!(appName ==~ /[A-Za-z0-9]+/)) throw new IllegalArgumentException("appName must contain only alphanumeric characters")
        }

        Map newFields(f) {
            Map f2 = [:]
            f2 << [cartridge: cartridge, app_name: appName, debug: ""+debug]
            f2 << f
        }
        /** returns info on an app; note, throws IllegalState on http error response 500 if doesn't exist */
        public Object status() {
            doPost(newFields( [action: "status"] ))
        }
        /** creates (and starts) an app */
        public Object create() {
            doPost(newFields( [action: "configure"] ))
        }
        /** deletes (and stops) an app */
        public Object destroy() {
            doPost(newFields( [action: "deconfigure"] ))
        }
    }

}
