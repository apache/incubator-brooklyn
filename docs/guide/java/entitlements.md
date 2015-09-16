---
title: Entitlements
layout: website-normal
---

Brooklyn supports a plug-in system for defining "entitlements" -- 
essentially permissions.

Any entitlement scheme can be implemented by supplying a class which implements one method on one class:

    public interface EntitlementManager {
      public <T> boolean isEntitled(@Nullable EntitlementContext context, @Nonnull EntitlementClass<T> entitlementClass, @Nullable T entitlementClassArgument);
    }

This answers the question who is allowed do what to whom, looking at the following fields:

* `context`: the user who is logged in and is attempting an action
  (extensions can contain additional metadata)
* `entitlementClass`: the type of action being queried, e.g. `DEPLOY_APPLICATION` or `SEE_SENSOR`
  (declared in the class `Entitlements`)
* `entitlementClassArgument`: details of the action being queried,
  such as the blueprint in the case of `DEPLOY_APPLICATION` or the entity and sensor name in the case
  of `SEE_SENSOR`

To set a custom entitlements manager to apply across the board, simply use:

    brooklyn.entitlements.global=org.apache.brooklyn.core.mgmt.entitlement.AcmeEntitlementManager

The example above refers to a sample manager which is included in the test JARs of Brooklyn,
which you can see [here]({{ site.brooklyn.url.git }}/core/src/test/java/org/apache/brooklyn/core/mgmt/entitlement/AcmeEntitlementManagerTest.java),
and include in your project by adding the core tests JAR to your `dropins` folder.

There are some entitlements schemes which exist out of the box, so for a simpler setup,
see [Operations: Entitlements]({{ site.path.guide }}/ops/brooklyn_properties.html#entitlements). 

There are also more complex schemes which some users have developed, including LDAP extensions 
which re-use the LDAP authorization support in Brooklyn, 
allowing permissions objects to be declared in LDAP leveraging regular expressions.
For more information on this, ask on IRC or the mailing list,
and see 
{% include java_link.html class_name="EntitlementManager" package_path="org/apache/brooklyn/api/mgmt/entitlement" project_subpath="api" %}.

