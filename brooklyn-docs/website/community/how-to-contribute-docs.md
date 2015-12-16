---
layout: website-normal
title: Contributing Documentation
navgroup: community
---

Welcome and thank you for your interest in contributing to Apache Brooklyn! This guide will take you through the
process of making contributions to the Apache Brooklyn website and documentation.


Quick Edits
-----------

The easiest way to contribute improvements to the doc is with the *Edit this page* 
button at the bottom of most pages. This will take you to the GitHub repository where
you can immediately begin editing the file.

This approach makes editing easy, as you do not need to
clone the repository to your workstation and make changes there; they can be
changed directly on the GitHub website for the repository.
(You will need a GitHub account but this is free and easy to create.)

Once you have made your edits:

* In the short form titled *Propose file change*,
  provide a short description of the change in the first box
* Optionally, provide a longer description in the second box. 
  If your change fixes or addresses a Jira issue, be sure to mention it.
* Next click *Propose file change* to prepare a pull request
* Finally click *Create pull request* to notify the team of your proposed change.
  The community will review it and merge and update the web site as necessary.


Bigger Contributions
--------------------

While the *Edit this page* button is great for quickly editing a single page, if
you want to do anything that involves editing multiple pages, you will need to
fork and clone the repository and make the changes on your own workstation.

For this, you should first review the general tips on [How to Contribute](../developers/how-to-contribute.html).

Next, you’ll want to become familiar with the `docs/` folder in the Brooklyn codebase where the docs live.
In particular, note that the Brooklyn documentation is split into two parts:

- **The main website and shared documentation**. This covers the root website
  and all pages that are not part of the version-specific user guide.
  Content for this is in the `website` directory.
  
- **Version-specific user guide**. These pages have a URL with a path that
  begins /v/*version-number*: for example,
  https://brooklyn.apache.org/v/0.8.0-incubating and {% comment %}BROOKLYN_VERSION{% endcomment %}
  the special *latest* set at https://brooklyn.apache.org/v/latest. Content for this is in the `guide` directory.

The main user guide shown on this site is for the most recent stable version,
currently {{ site.brooklyn-stable-version }}.
Guides for other versions are available [here](../meta/versions.html).


For More Information
--------------------

Advanced instructions for building, previewing and publishing docs are in a `README.md` file
in the `docs` folder; see those instructions
[here](https://github.com/apache/incubator-brooklyn/tree/master/docs/README.md).
