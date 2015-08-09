---
title: brooklyn.properties
layout: website-normal
children:
- { section: Quick Setup }
- { section: Locations }
- { section: Java }
- { section: Authentication }
- { section: Entitlements }
- { section: HTTPS Configuration }
---

{% include fields.md %}

The file `~/.brooklyn/brooklyn.properties` is read when Brooklyn starts
to load server configuration values.
A different properties file can be specified either additionally or instead
through [CLI options](launch.html#configuration-files).

A template [brooklyn.properties]({{brooklyn_properties_url_path}}) file is available,
with abundant comments.


## Quick Setup

The most common properties set in this file are for access control.
Without this, Brooklyn will bind only to localhost or will create a random
password written to the log for use on other networks.
The simplest way to specify users and passwords is:
 
{% highlight properties %}
brooklyn.webconsole.security.users=admin,bob
brooklyn.webconsole.security.user.admin.password=AdminPassw0rd
brooklyn.webconsole.security.user.bob.password=BobPassw0rd
{% endhighlight %}

The properties file *must* have permissions 600 
(i.e. readable and writable only by the file's owner),
for some security.

In many cases, it is preferable instead to use an external credentials store such as LDAP
or at least to have passwords in this file.
Information on configuring these is [below](#authentication). 

If coming over a network it is highly recommended additionally to use `https`.
This can be configured with:

{% highlight properties %}
brooklyn.webconsole.security.https.required=true
{% endhighlight %}

More information, including setting up a certificate, is described [further below](#https-configuration).


## Locations

Information on defining locations in the `brooklyn.properties` file is available [here](locations/).


## Java

Arbitrary data can be set in the `brooklyn.properties`.
This can be accessed in java using `ManagementContext.getConfig(KEY)`.


## Authentication

**Security Providers** are the mechanism by which different authentication authorities are plugged in to Brooklyn.
These can be configured by specifying `brooklyn.webconsole.security.provider` equal 
to the name of a class implementing `SecurityProvider`.
An implementation of this could point to Spring, LDAP, OpenID or another identity management system.

The default implementation, `ExplicitUsersSecurityProvider`, reads from a list of users and passwords
which should be specified as configuration parameters e.g. in `brooklyn.properties`.
This configuration could look like:

{% highlight properties %}
brooklyn.webconsole.security.users=admin
brooklyn.webconsole.security.user.admin.salt=OHDf
brooklyn.webconsole.security.user.admin.sha256=91e16f94509fa8e3dd21c43d69cadfd7da6e7384051b18f168390fe378bb36f9
{% endhighlight %}

The `users` line should contain a comma-separated list. The special value `*` is accepted to permit all users.

To generate this, the brooklyn CLI can be used:
{% highlight bash %}
brooklyn generate-password --user admin

Enter password: 
Re-enter password: 

Please add the following to your brooklyn.properies:

brooklyn.webconsole.security.users=admin
brooklyn.webconsole.security.user.admin.salt=OHDf
brooklyn.webconsole.security.user.admin.sha256=91e16f94509fa8e3dd21c43d69cadfd7da6e7384051b18f168390fe378bb36f9
{% endhighlight %}

Alternatively, in dev/test environments where a lower level of security is required,
the syntax `brooklyn.webconsole.security.user.<username>=<password>` can be used for
each `<username>` specified in the `brooklyn.webconsole.security.users` list.

Other security providers available include:

### No one

`brooklyn.webconsole.security.provider=org.apache.brooklyn.rest.security.provider.BlackholeSecurityProvider`
will block all logins (e.g. if not using the web console)

### No security

`brooklyn.webconsole.security.provider=org.apache.brooklyn.rest.security.provider.AnyoneSecurityProvider`
will allow logins with no credentials (e.g. in secure dev/test environments) 

### LDAP

`brooklyn.webconsole.security.provider=org.apache.brooklyn.rest.security.provider.LdapSecurityProvider`
will cause Brooklyn to call to an LDAP server to authenticate users;
The other things you need to set in `brooklyn.properties` are:

* `brooklyn.webconsole.security.ldap.url` - ldap connection url
* `brooklyn.webconsole.security.ldap.realm` - ldap dc parameter (domain)
* `brooklyn.webconsole.security.ldap.ou` *optional, by default it set to Users* -  ldap ou parameter

**brooklyn.properties example configuration:**

```
brooklyn.webconsole.security.provider=org.apache.brooklyn.rest.security.provider.LdapSecurityProvider
brooklyn.webconsole.security.ldap.url=ldap://localhost:10389/????X-BIND-USER=uid=admin%2cou=system,X-BIND-PASSWORD=secret,X-COUNT-LIMIT=1000
brooklyn.webconsole.security.ldap.realm=example.com
```

After you setup the brooklyn connection to your LDAP server, you can authenticate in brooklyn using your cn (e.g. John Smith) and your password.
`org.apache.brooklyn.rest.security.provider.LdapSecurityProvider` searches in the LDAP tree in LDAP://cn=John Smith,ou=Users,dc=example,dc=com

If you want to customize the ldap path or something else which is particular to your LDAP setup you can extend `LdapSecurityProvider` class or implement from scratch the `SecurityProvider` interface.

## Entitlements

In addition to login access, fine-grained permissions -- including 
seeing entities, creating applications, seeing sensors, and invoking effectors --
can be defined on a per-user *and* per-target (e.g. which entity/effector) basis
using a plug-in **Entitlement Manager**.

This can be set globally with the property:

{% highlight properties %}
brooklyn.entitlements.global=<class>
{% endhighlight %}

The default entitlement manager is one which responds to per-user entitlement rules,
and understands:

* `root`:  full access, including to the Groovy console
* `readonly`:  read-only access to almost all information
* `minimal`:  access only to server stats, for use by monitoring systems

These keywords are also understood at the `global` level, so to grant full access to `admin`
but limited access to other authenticated users and `readonly, 
you can write:

{% highlight properties %}
brooklyn.entitlements.global=readonly
brooklyn.entitlements.perUser.admin=root
brooklyn.entitlements.perUser.support=readonly
brooklyn.entitlements.perUser.metrics=minimal
{% endhighlight %}

Under the covers this invokes the `PerUserEntitlementManager`, 
with a `default` set (and if not specified `default` defaults to `minimal`); 
so the above can equivalently be written:

{% highlight properties %}
brooklyn.entitlements.global=brooklyn.management.entitlement.PerUserEntitlementManager
brooklyn.entitlements.perUser.default=readonly
brooklyn.entitlements.perUser.admin=root
brooklyn.entitlements.perUser.support=readonly
brooklyn.entitlements.perUser.metrics=minimal
{% endhighlight %}

For more information, see 
[Java: Entitlements]({{ site.path.guide }}/java/entitlements.html).
or
{% include java_link.html class_name="EntitlementManager" package_path="brooklyn/management/entitlement" project_subpath="api" %}.



## HTTPS Configuration

To enable https, you will need a server certificate in a java keystore. To create a self-signed certificate, you can use the
following command:

{% highlight bash %}
% keytool -genkey -keyalg RSA -alias brooklyn -keystore <path-to-keystore-directory>/server.key -storepass mypassword -validity 360 -keysize 2048
{% endhighlight %}

You will then be prompted to enter you name and organization details. This will create a keystore with the password `mypassword`
- you should use your own secure password, which will be the same password used in your brooklyn.properties (below). 
You will also need to replace `<path-to-keystore-directory>` with the full path of the folder where you wish to store your
keystore. 

The certificate generated will be a self-signed certificate and will not have a CN field identifying the website server 
name, which will cause a warning to be displayed by the browser when viewing the page. For production servers, a valid signed 
certificate from a trusted certifying authority should be used instead

To enable HTTPS in Brooklyn, add the following to your brooklyn.properties:

{% highlight properties %}
brooklyn.webconsole.security.https.required=true
brooklyn.webconsole.security.keystore.url=<path-to-keystore-directory>/server.key
brooklyn.webconsole.security.keystore.password=mypassword
brooklyn.webconsole.security.keystore.certificate.alias=brooklyn
{% endhighlight %}
