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
package brooklyn.util.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

import brooklyn.util.exceptions.Exceptions;

/** A fluent API which simplifies generating certificates (signed keys) */
/* we use deprecated X509V3CertificateGenerator for now because official replacement,
 * X509v3CertificateBuilder drags in an add'l dependency (bcmail) and is harder to use. */
@SuppressWarnings("deprecation")
public class FluentKeySigner {
    
    protected X500Principal issuerPrincipal;
    protected KeyPair issuerKey;

    protected SecureRandom srand = new SecureRandom();
    
    protected Date validityStartDate, validityEndDate;
    protected BigInteger serialNumber;
    
    protected String signatureAlgorithm = "MD5WithRSAEncryption";
    protected AuthorityKeyIdentifierStructure authorityKeyIdentifier;
    protected X509Certificate authorityCertificate;

    public FluentKeySigner(X500Principal issuerPrincipal, KeyPair issuerKey) {
        this.issuerPrincipal = issuerPrincipal;
        this.issuerKey = issuerKey;
        validFromDaysAgo(7);
        validForYears(10);
    }
    public FluentKeySigner(String issuerCommonName, KeyPair issuerKey) {
        this(SecureKeys.getX500PrincipalWithCommonName(issuerCommonName), issuerKey);
    }
    
    public FluentKeySigner(String issuerCommonName) {
        this(issuerCommonName, SecureKeys.newKeyPair());
    }

    public FluentKeySigner(X509Certificate caCert, KeyPair caKey) {
        this(caCert.getIssuerX500Principal(), caKey);
        authorityCertificate(caCert);
    }
    
    public KeyPair getKey() {
        return issuerKey;
    }
    
    public X500Principal getPrincipal() {
        return issuerPrincipal;
    }
    
    public String getCommonName() {
        return (String) new X509Principal(issuerPrincipal.getName()).getValues(X509Name.CN).elementAt(0);
    }
    
    public X509Certificate getAuthorityCertificate() {
        return authorityCertificate;
    }
    
    public FluentKeySigner validFromDaysAgo(long days) {
        return validFrom(new Date( (System.currentTimeMillis() / (1000L*60*60*24) - days) * 1000L*60*60*24));            
    }

    public FluentKeySigner validFrom(Date d) {
        validityStartDate = d;
        return this;
    }

    public FluentKeySigner validForYears(long years) {
        return validUntil(new Date( (System.currentTimeMillis() / (1000L*60*60*24) + 365*years) * 1000L*60*60*24));            
    }

    public FluentKeySigner validUntil(Date d) {
        validityEndDate = d;
        return this;
    }

    /** use a hard-coded serial number; or make one up, if null */
    public FluentKeySigner serialNumber(BigInteger serialNumber) {
        this.serialNumber = serialNumber;
        return this;
    }

    public FluentKeySigner signatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
        return this;
    }

    public FluentKeySigner authorityCertificate(X509Certificate certificate) {
        try {
            authorityKeyIdentifier(new AuthorityKeyIdentifierStructure(certificate));
            this.authorityCertificate = certificate;
            return this;
        } catch (CertificateParsingException e) {
            throw Exceptions.propagate(e);
        }
    }

    public FluentKeySigner authorityKeyIdentifier(AuthorityKeyIdentifierStructure authorityKeyIdentifier) {
        this.authorityKeyIdentifier = authorityKeyIdentifier;
        return this;
    }
    
    public FluentKeySigner selfsign() {
        if (authorityCertificate!=null) throw new IllegalStateException("Signer already has certificate");
        authorityCertificate(newCertificateFor(getCommonName(), getKey()));
        return this;
    }
    
    public X509Certificate newCertificateFor(X500Principal subject, PublicKey keyToCertify) {
        try {
            X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();

            v3CertGen.setSerialNumber(
                    serialNumber != null ? serialNumber :
                        // must be positive
                        BigInteger.valueOf(srand.nextLong()).abs().add(BigInteger.ONE));  
            v3CertGen.setIssuerDN(issuerPrincipal);  
            v3CertGen.setNotBefore(validityStartDate);  
            v3CertGen.setNotAfter(validityEndDate);
            v3CertGen.setSignatureAlgorithm(signatureAlgorithm);   

            v3CertGen.setSubjectDN(subject);  
            v3CertGen.setPublicKey(keyToCertify);  

            v3CertGen.addExtension(X509Extension.subjectKeyIdentifier, false,
                    new SubjectKeyIdentifierStructure(keyToCertify));

            if (authorityKeyIdentifier!=null)
                v3CertGen.addExtension(X509Extension.authorityKeyIdentifier, false,
                        authorityKeyIdentifier);

            X509Certificate pkCertificate = v3CertGen.generate(issuerKey.getPrivate(), "BC");
            return pkCertificate;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public X509Certificate newCertificateFor(String commonName, PublicKey key) {
//        SecureKeys.getX509PrincipalWithCommonName(commonName)
        return newCertificateFor(
                SecureKeys.getX500PrincipalWithCommonName(commonName)
//                new X509Principal("CN=" + commonName + ", OU=None, O=None, L=None, C=None")
                , key);
    }

    public X509Certificate newCertificateFor(String commonName, KeyPair key) {
        return newCertificateFor(commonName, key.getPublic());
    }

}
