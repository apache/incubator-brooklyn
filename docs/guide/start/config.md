---
title: Configuring Brooklyn
title_in_menu: Configuring Brooklyn
layout: guide-normal
menu_parent: index.md
---

Brooklyn reads startup configuration from a file `~/.brooklyn/brooklyn.properties`, by default.
You can create this from a template [brooklyn.properties]({{brooklyn_properties_url_path}}) file which you edit.

## Configuring Security

To configure Brooklyn to run on a public IP address, security should be enabled.
The simplest way is to define a user and password in `~/.brooklyn/brooklyn.properties`
(described above): 

    brooklyn.webconsole.security.users=admin
    brooklyn.webconsole.security.user.admin.password=s3cr3t

Other modes, including LDAP, are described in this file.

The other common setting is to run under https (on port 8443 by default):

    brooklyn.webconsole.security.https.required=true

## Configuring a Location

Brooklyn deploys applications to locations. These locations
can be clouds, machines with fixed IPs or localhost (for testing).
Their configuration can be specified in `~/.brooklyn/brooklyn.properties` (described above),
and then these locations can be easily selected within Brooklyn.
Alternatively this information can be specified in the YAML when applications are deployed,
without needing to set it in `brooklyn.properties`.

Some sample settings for this are:

    brooklyn.location.jclouds.aws-ec2.identity = AKA_YOUR_ACCESS_KEY_ID
    brooklyn.location.jclouds.aws-ec2.credential = <access-key-hex-digits>
    brooklyn.location.named.aws-california = jclouds:aws-ec2:us-west-1
    brooklyn.location.named.aws-california.displayName = AWS US West 1 (CA)

    brooklyn.location.jclouds.softlayer.identity = username
    brooklyn.location.jclouds.softlayer.credential = <private-key-hex-digits>
    brooklyn.location.named.softlayer-dal05 = jclouds:softlayer:dal05
    brooklyn.location.named.softlayer-dal05.displayName = Softlayer Dallas

If you want to test Brooklyn on localhost, follow [these instructions]({{site.path.guide}}/use/guide/locations/) 
to ensure that your Brooklyn can access your machine.

Once updated, restart Brooklyn (or reload the properties within the web GUI).
