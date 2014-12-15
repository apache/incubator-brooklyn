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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects;

public class ProxySslConfig implements Serializable {

    private static final long serialVersionUID = -2692754611458939617L;

    private static final Logger log = LoggerFactory.getLogger(ProxySslConfig.class);
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @SetFromFlag protected String certificateSourceUrl;
        @SetFromFlag protected String keySourceUrl;
        @SetFromFlag protected String certificateDestination;
        @SetFromFlag protected String keyDestination;
        @SetFromFlag protected boolean targetIsSsl = false;
        @SetFromFlag protected boolean reuseSessions = false;

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
    
    public static ProxySslConfig fromMap(Map<?,?> map) {
        Builder b = new Builder();
        Map<?, ?> unused = FlagUtils.setFieldsFromFlags(map, b);
        if (!unused.isEmpty()) log.warn("Unused flags when populating "+b+" (ignoring): "+unused);
        return b.build();
    }

    private String certificateSourceUrl;
    private String keySourceUrl;
    private String certificateDestination;
    private String keyDestination;
    private boolean targetIsSsl = false;
    private boolean reuseSessions = false;

    public ProxySslConfig() { }

    protected ProxySslConfig(Builder builder) {
        certificateSourceUrl = builder.certificateSourceUrl;
        keySourceUrl = builder.keySourceUrl;
        certificateDestination = builder.certificateDestination;
        keyDestination = builder.keyDestination;
        targetIsSsl = builder.targetIsSsl;
        reuseSessions = builder.reuseSessions;
    }

    /**
     * URL for the SSL certificates required at the server.
     * <p>
     * Corresponding nginx settings:
     * <pre>
     *     ssl                  on;
     *     ssl_certificate      www.example.com.crt;
     *     ssl_certificate_key  www.example.com.key;
     * </pre>
     * Okay (in nginx) for key to be null if certificate contains both as per setup at
     * http://nginx.org/en/docs/http/configuring_https_servers.html
     * <p>
     * Proxy object can be set on nginx instance to apply site-wide,
     * and to put multiple servers in the certificate file
     * <p>
     * The brooklyn entity will install the certificate/key(s) on the server.
     * (however it will not currently merge multiple certificates.
     * if conflicting certificates are attempted to be installed nginx will complain.)
     */
    public String getCertificateSourceUrl() {
        return certificateSourceUrl;
    }

    public void setCertificateSourceUrl(String certificateSourceUrl) {
        this.certificateSourceUrl = certificateSourceUrl;
    }

    /** @see #getCertificateSourceUrl()} */
    public String getKeySourceUrl() {
        return keySourceUrl;
    }

    public void setKeySourceUrl(String keySourceUrl) {
        this.keySourceUrl = keySourceUrl;
    }

    /**
     * Sets the {@code ssl_certificate_path} to be used within the generated
     * {@link LoadBalancer} configuration.
     * <p>
     * If set to null, Brooklyn will use an auto generated path.
     * <p>
     * If {@link #getCertificateSourceUrl() certificateSourceUrl} is set     *
     * then Brooklyn will copy the certificate the destination.
     * <p>
     * Setting this field is useful if there is a {@code certificate} on the
     * nginx machine you want to make use of.
     */
    public String getCertificateDestination() {
        return certificateDestination;
    }

    public void setCertificateDestination(String certificateDestination) {
        this.certificateDestination = certificateDestination;
    }

    /**
     * Sets the {@code ssl_certificate_key} path to be used within the generated
     * {@link LoadBalancer} configuration.
     * <p>
     * If set to null, Brooklyn will use an auto generated path.
     * <p>
     * If {@link #getKeySourceUrl() keySourceUrl} is set then Brooklyn will copy the
     * certificate to the destination.
     * <p>
     * Setting this field is useful if there is a {@code certificate_key} on the
     * nginx machine you want to make use of.
     */
    public String getKeyDestination() {
        return keyDestination;
    }

    public void setKeyDestination(String keyDestination) {
        this.keyDestination = keyDestination;
    }

    /**
     * Whether the downstream server (if mapping) also expects https; default false.
     */
    public boolean getTargetIsSsl() {
        return targetIsSsl;
    }

    public void setTargetIsSsl(boolean targetIsSsl) {
        this.targetIsSsl = targetIsSsl;
    }

    /**
     * Whether to reuse SSL validation in the server (performance).
     * <p>
     * Corresponds to nginx setting {@code proxy_ssl_session_reuse on|off}.
     */
    public boolean getReuseSessions() {
        return reuseSessions;
    }

    public void setReuseSessions(boolean reuseSessions) {
        this.reuseSessions = reuseSessions;
    }

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
