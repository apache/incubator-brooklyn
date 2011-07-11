/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.location.basic.aws;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

import javax.annotation.Nullable;

import org.jclouds.compute.reference.ComputeServiceConstants;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * represents the configuration that will be passed to the underlying jclouds
 * {@link org.jclouds.compute.ComputeService}
 *
 * @author Adrian Cole
 *
 */
public class JCloudsConfiguration implements Serializable {
   private static final long serialVersionUID = 9040001259986153485L;

   public static class Builder {
      private String provider;
      private String identity;
      private String credential;
      private String imageId;
      private String hardwareId;
      private String publicKey;
      private String privateKey;
      private Properties overrides = getDefaultProperties();
      private String imagePattern;
      private String imageOwner;
      private String imageVersionPattern;
      private int minRam;
      private String vcloudIpAddressAllocationMode;
      private Properties restProperties;

      static Properties getDefaultProperties() {
         Properties defaultProperties = new Properties();
         // by using only cloudsoft images, will speed up execution
         defaultProperties.setProperty("jclouds.ec2.ami-owners", "761990928256");
         defaultProperties.setProperty(ComputeServiceConstants.PROPERTY_TIMEOUT_SCRIPT_COMPLETE, ""+5*60*1000);
         return defaultProperties;
      }

      public Builder(String provider, String identity) {
         provider(checkNotNull(provider, "provider"));
         identity(checkNotNull(identity, "identity"));
      }

      /**
       *
       * @see JCloudsConfiguration#getProvider()
       */
      public Builder overrides(Properties overrides) {
         this.overrides = overrides;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getProvider()
       */
      public Builder provider(String provider) {
         this.provider = provider;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getIdentity()
       */
      public Builder identity(String identity) {
         this.identity = identity;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getCredential()
       */
      public Builder credential(String credential) {
         this.credential = credential;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getImageId()
       */
      public Builder imageId(String imageId) {
         this.imageId = imageId;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getHardwareId()
       */
      public Builder hardwareId(String hardwareId) {
         this.hardwareId = hardwareId;
         return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getPublicKey()
       */
      public Builder publicKey(String publicKey) {
         checkArgument(checkNotNull(publicKey, "publicKey").startsWith("ssh-rsa"), "key should start with ssh-rsa");
         this.publicKey = publicKey;
         return this;
      }

      /**
       * @see JCloudsConfiguration#getPublicKey()
       *
       * @throws IOException
       *            if there is a problem reading the file
       */
      public Builder publicKey(File publicKey) throws IOException {
         return publicKey(Files.toString(checkNotNull(publicKey, "publicKey"), Charsets.UTF_8));
      }

      /**
       *
       * @see JCloudsConfiguration#getPrivateKey()
       */
      public Builder privateKey(String privateKey) {
         checkArgument(checkNotNull(privateKey, "privateKey").startsWith("-----BEGIN RSA PRIVATE KEY-----"),
               "key should start with -----BEGIN RSA PRIVATE KEY-----");
         this.privateKey = privateKey;
         return this;
      }

      public Builder imagePattern(String val) {
          this.imagePattern = val;
          return this;
      }

      public Builder imageOwner(String val) {
          this.imageOwner = val;
          return this;
      }

      public Builder imageVersionPattern(String val) {
          this.imageVersionPattern = val;
          return this;
      }

      public Builder minRam(int val) {
          this.minRam = val;
          return this;
      }

      public Builder vcloudIpAddressAllocationMode(String val) {
          this.vcloudIpAddressAllocationMode = val;
          return this;
      }

      public Builder restProperties(Properties val) {
          this.restProperties = val;
          return this;
      }

      /**
       *
       * @see JCloudsConfiguration#getPrivateKey()
       * @throws IOException
       *            if there is a problem reading the file
       */
      public Builder privateKey(File privateKey) throws IOException {
         return privateKey(Files.toString(checkNotNull(privateKey, "privateKey"), Charsets.UTF_8));
      }

      public JCloudsConfiguration build() {
         return new JCloudsConfiguration(this);
      }
   }

   private final String provider;
   private final String identity;
   @Nullable
   private final String credential;
   private final Properties overrides;
   @Nullable
   private final String imageId;
   @Nullable
   private final String hardwareId;
   @Nullable
   private final String publicKey;
   @Nullable
   private final String privateKey;
   @Nullable
   private String imagePattern;
   @Nullable
   private String imageOwner;
   @Nullable
   private String imageVersionPattern;
   @Nullable
   private Properties restProperties;
   @Nullable
   private String vcloudIpAddressAllocationMode;

   private int minRam;

   public JCloudsConfiguration(Builder builder) {
      this.provider = checkNotNull(builder.provider, "provider");
      this.identity = checkNotNull(builder.identity, "identity");
      this.credential = builder.credential;
      this.overrides = checkNotNull(builder.overrides, "overrides");
      this.imageId = builder.imageId;
      this.hardwareId = builder.hardwareId;
      this.publicKey = builder.publicKey;
      this.privateKey = builder.privateKey;
      this.imagePattern = builder.imagePattern;
      this.imageOwner = builder.imageOwner;
      this.imageVersionPattern = builder.imageVersionPattern;
      this.minRam = builder.minRam;
      this.vcloudIpAddressAllocationMode = builder.vcloudIpAddressAllocationMode;
      this.restProperties = builder.restProperties;
   }

   /**
    * @return provider of a compute cloud, for example {@code ec2}, {@code gogrid}
    * @see org.jclouds.compute.ComputeServiceContextFactory#createContext(String, String, String)
    */
   public String getProvider() {
      return provider;
   }

   /**
    * @return identity used to login to the {@link #getProvider() provider}
    * @see org.jclouds.compute.ComputeServiceContextFactory#createContext(String, String, String)
    */
   public String getIdentity() {
      return identity;
   }

   /**
    * @return credential corresponding to {@link #getIdentity() identity}, if necessary on the
    *         {@link #getProvider() provider}
    * @see org.jclouds.compute.ComputeServiceContextFactory#createContext(String, String, String)
    */
   @Nullable
   public String getCredential() {
      return credential;
   }

   /**
    * @return id implying a base operating system and potentially configured software, scoped to the
    *         service. ex. {@code us-east-1/ami-feras32}
    *
    * @see org.jclouds.compute.domain.TemplateBuilder#imageId(String)
    */
   @Nullable
   public String getImageId() {
      return imageId;
   }

   /**
    * @return hardware id specifying exact virtual or hardware configuration. ex. {@code m1.small}
    *
    * @see org.jclouds.compute.domain.TemplateBuilder#hardwareId(String)
    */
   @Nullable
   public String getHardwareId() {
      return hardwareId;
   }

   /**
    * @return The rsa public key which is authorized to login to your on the cloud nodes.
    *
    * @see org.jclouds.compute.options.TemplateOptions#authorizePublicKey(String)
    */
   @Nullable
   public String getPublicKey() {
      return publicKey;
   }

   /**
    * @return rsa private key which is used as the login identity on the cloud nodes.
    * @see org.jclouds.compute.options.TemplateOptions#installPrivateKey(String)
    */
   @Nullable
   public String getPrivateKey() {
      return privateKey;
   }

   /**
    *
    * @return overriding properties to be sent to jclouds
    * @see org.jclouds.compute.ComputeServiceContextFactory#createContext(String, String, String,
    *      Iterable, Properties)
    */
   public Properties getOverrides() {
      return overrides;
   }

    public String getImagePattern() {
        return imagePattern;
    }

    public String getImageOwner() {
        return imageOwner;
    }

    public String getImageVersionPattern() {
        return imageVersionPattern;
    }


    public int getMinRam() {
        return minRam;
    }

    public String getVcloudIpAddressAllocationMode() {
        return vcloudIpAddressAllocationMode;
    }

    public Properties getRestProperties() {
        return restProperties;
    }
}
