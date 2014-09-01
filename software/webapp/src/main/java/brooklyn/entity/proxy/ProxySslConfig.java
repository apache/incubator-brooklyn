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
package brooklyn.entity.proxy;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;

public class ProxySslConfig implements Serializable {

    private static final long serialVersionUID = -2692754611458939617L;
    private static final Logger LOG = LoggerFactory.getLogger(ProxySslConfig.class);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Setup type coercion. */
    @SuppressWarnings("rawtypes")
    public static void init() {
        if (initialized.getAndSet(true)) return;

        TypeCoercions.registerAdapter(Map.class, ProxySslConfig.class, new Function<Map, ProxySslConfig>() {
            @Override
            public ProxySslConfig apply(final Map input) {
                Map map = MutableMap.copyOf(input);
                ProxySslConfig.Builder sslConfig = ProxySslConfig.builder()
                        .certificateSourceUrl((String) map.remove("certificateSourceUrl"))
                        .keySourceUrl((String) map.remove("keySourceUrl"))
                        .certificateDestination((String) map.remove("certificateDestination"))
                        .keyDestination((String) map.remove("keyDestination"));
                Object targetIsSsl = map.remove("targetIsSsl");
                if (targetIsSsl != null) {
                    sslConfig.targetIsSsl(TypeCoercions.coerce(targetIsSsl, Boolean.TYPE));
                }
                Object reuseSessions = map.remove("reuseSessions");
                if (reuseSessions != null) {
                    sslConfig.reuseSessions(TypeCoercions.coerce(reuseSessions, Boolean.TYPE));
                }
                if (!map.isEmpty()) {
                    LOG.info("Extra unused keys found in ProxySslConfig config: [{}]",
                            Joiner.on(",").withKeyValueSeparator("=").join(map));
                }
                return sslConfig.build();
            }
        });
    }

    static {
        init();
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String certificateSourceUrl;
        protected String keySourceUrl;
        protected String certificateDestination;
        protected String keyDestination;
        protected boolean targetIsSsl = false;
        protected boolean reuseSessions = false;
        
        public Builder certificateSourceUrl(String val) {
            certificateSourceUrl = val; return this;
        }
        public Builder keySourceUrl(String val) {
            keySourceUrl = val; return this;
        }
        public Builder certificateDestination(String val) {
            certificateDestination = val; return this;
        }
        public Builder keyDestination(String val) {
            keyDestination = val; return this;
        }
        public Builder targetIsSsl(boolean val) {
            targetIsSsl = val; return this;
        }
        public Builder reuseSessions(boolean val) {
            reuseSessions = val; return this;
        }
        public ProxySslConfig build() {
            ProxySslConfig result = new ProxySslConfig(this);
            return result;
        }
    }
    
    /** 
     * url's for the SSL certificates required at the server
     * <p>
     * nginx settings:
     *     ssl                  on;
     *     ssl_certificate      www.example.com.crt;
     *     ssl_certificate_key  www.example.com.key;
     *  <p>
     *  okay (in nginx) for key to be null if certificate contains both as per setup at
     *  http://nginx.org/en/docs/http/configuring_https_servers.html
     *  <p>
     *  proxy object can be set on nginx instance to apply site-wide,
     *  and to put multiple servers in the certificate file
     *  <p>
     *  the brooklyn entity will install the certificate/key(s) on the server.
     *  (however it will not currently merge multiple certificates.
     *  if conflicting certificates are attempted to be installed nginx will complain.) 
     */
    String certificateSourceUrl;

    String keySourceUrl;

    /**
     * Sets the ssl_certificate path to be used within the generated LoadBalancer configuration. If set to null,
     * Brooklyn will use an auto generated path.
     *
     * If certificateSourceUrl, then Brooklyn will copy the certificate the certificateDestination.
     *
     * Setting this field is useful if there is a certificate on the nginx machine you want to make use of.
     */
    String certificateDestination;

    /**
     * Sets the ssl_certificate_key path to be used within the generated LoadBalancer configuration. If set to null,
     * Brooklyn will use an auto generated path.
     *
     * If keySourceUrl, then Brooklyn will copy the certificate the keyDestination.
     *
     * Setting this field is useful if there is a certificate_key on the nginx machine you want to make use of.
     */
    String keyDestination;

    /** whether the downstream server (if mapping) also expects https; default false */
    boolean targetIsSsl = false;

    /** whether to reuse SSL validation in the server (performance).
     * corresponds to nginx setting: proxy_ssl_session_reuse on|off */
    boolean reuseSessions = false;

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public ProxySslConfig() {
    }
    
    protected ProxySslConfig(Builder builder) {
        certificateSourceUrl = builder.certificateSourceUrl;
        keySourceUrl = builder.keySourceUrl;
        certificateDestination = builder.certificateDestination;
        keyDestination = builder.keyDestination;
        targetIsSsl = builder.targetIsSsl;
        reuseSessions = builder.reuseSessions;
    }
    
    public String getCertificateSourceUrl() {
        return certificateSourceUrl;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setCertificateSourceUrl(String certificateSourceUrl) {
        this.certificateSourceUrl = certificateSourceUrl;
    }

    public String getKeySourceUrl() {
        return keySourceUrl;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setKeySourceUrl(String keySourceUrl) {
        this.keySourceUrl = keySourceUrl;
    }

    public String getCertificateDestination() {
        return certificateDestination;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setCertificateDestination(String certificateDestination) {
        this.certificateDestination = certificateDestination;
    }

    public String getKeyDestination() {
        return keyDestination;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setKeyDestination(String keyDestination) {
        this.keyDestination = keyDestination;
    }

    public boolean getTargetIsSsl() {
        return targetIsSsl;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setTargetIsSsl(boolean targetIsSsl) {
        this.targetIsSsl = targetIsSsl;
    }

    public boolean getReuseSessions() {
        return reuseSessions;
    }

    /**
     * @deprecated since 0.7.0; use {@link ProxySslConfig#builder()}
     */
    @Deprecated
    public void setReuseSessions(boolean reuseSessions) {
        this.reuseSessions = reuseSessions;
    }

    // autogenerated hash code and equals; nothing special required

    @Override
    public int hashCode() {
        return Objects.hashCode(certificateSourceUrl, keySourceUrl, certificateDestination, keyDestination, reuseSessions, targetIsSsl);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProxySslConfig other = (ProxySslConfig) obj;

        return Objects.equal(certificateSourceUrl, other.certificateSourceUrl) &&
                Objects.equal(certificateDestination, other.certificateDestination) &&
                Objects.equal(keyDestination, other.keyDestination) &&
                Objects.equal(keySourceUrl, other.keySourceUrl) &&
                Objects.equal(reuseSessions, other.reuseSessions) &&
                Objects.equal(targetIsSsl, other.targetIsSsl);
    }
}
