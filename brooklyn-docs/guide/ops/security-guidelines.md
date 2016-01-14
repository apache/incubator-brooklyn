---
title: Security Guidelines
layout: website-normal
---

## Brooklyn Server

### Web-console and REST api

Users are strongly encouraged to use HTTPS, rather than HTTP.

The use of LDAP is encouraged, rather than basic auth.

Configuration of "entitlements" is encouraged, to lock down access to the REST api for different 
users.


### Brooklyn user

Users are strongly discouraged from running Brooklyn as root.

For production use-cases (i.e. where Brooklyn will never deploy to "localhost"), the user under 
which Brooklyn is running should not have `sudo` rights.


### Persisted State

Use of an object store is recommended (e.g. using S3 compliant or Swift API) - thus making
use of the security features offered by the chosen object store.

File-based persistence is also supported. Permissions of the files will automatically
be 600 (i.e. read-write only by the owner). Care should be taken for permissions of the
relevant mount points, disks and directories.


## Credential Storage

For credential storage, users are strongly encouraged to consider using the "externalised 
configuration" feature. This allows credentials to be retrieved from a store managed by you, 
rather than being stored within YAML blueprints or brooklyn.properties.

A secure credential store is strongly recommended, such as use of 
[HashiCorp's Vault](https://www.vaultproject.io) - see
`org.apache.brooklyn.core.config.external.vault.VaultExternalConfigSupplier`.


## Infrastructure Access

### Cloud Credentials and Access

Users are strongly encouraged to create separate cloud credentials for Brooklyn's API access.

Users are also encouraged to (where possible) configure the cloud provider for only minimal API 
access (e.g. using AWS IAM).

<!--
TODO: We should document the minimum requirements for AWS IAM required by Brooklyn
-->


### VM Image Credentials

Users are strongly discouraged from using hard-coded passwords within VM images. Most cloud 
providers/APIs provide a mechanism to instead set an auto-generated password or to create an 
entry in `~/.ssh/authorized_keys` (prior to the VM being returned by the cloud provider).

If a hard-coded credential is used, then Brooklyn can be configured with this "loginUser" and 
"loginUser.password" (or "loginUser.privateKeyData"), and can change the password and disable 
root login.


### VM Users

It is strongly discouraged to use the root user on VMs being created or managed by Brooklyn.


### SSH keys

Users are strongly encouraged to use SSH keys for VM access, rather than passwords.

This SSH key could be a file on the Brooklyn server. However, a better solution is to use the 
"externalised configuration" to return the "privateKeyData". This better supports upgrading of 
credentials.


## Install Artifact Downloads

When Brooklyn executes scripts on remote VMs to install software, it often requires downloading 
the install artifacts. For example, this could be from an RPM repository or to retrieve `.zip` 
installers.

By default, the RPM repositories will be whatever the VM image is configured with. For artifacts 
to be downloaded directly, these often default to the public site (or mirror) for that software 
product.

Where users have a private RPM repository, it is strongly encouraged to ensure the VMs are 
configured to point at this.

For other artifacts, users should consider hosting these artifacts in their own web-server and 
configuring Brooklyn to use this. See the documentation for 
`org.apache.brooklyn.core.entity.drivers.downloads.DownloadProducerFromProperties`.

