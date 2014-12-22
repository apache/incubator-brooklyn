---
title: Entitlements
layout: guide-normal
toc: ../guide_toc.json
categories: [use, guide]
---

Brooklyn supports a plug-in system for defining ``entitlements'' -- 
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

    brooklyn.entitlements.global=brooklyn.management.entitlement.AcmeEntitlementManager

The example above refers to a sample manager which is included in the test JARs of Brooklyn,
which you can see [here]({{ site.brooklyn.url.git }}/core/src/test/java/brooklyn/management/entitlement/AcmeEntitlementManagerTest.java),
and include in your project by adding the core tests JAR to your `dropins` folder.


## Please Make it Simpler

There are some entitlements schemes which exist out of the box,
and you can use them simply by editing your `brooklyn.properties`:

    brooklyn.entitlements.global=brooklyn.management.entitlement.PerUserEntitlementManager
    brooklyn.entitlements.perUser.admin=root
    brooklyn.entitlements.perUser.support=readonly
    brooklyn.entitlements.perUser.metrics=minimal


Some users have gone further and build LDAP extensions which re-use the LDAP authorization support
in Brooklyn, allowing permissions objects to be declared in LDAP and used to make entitlement decisions.
For more information on this, ask on IRC or the mailing list.
