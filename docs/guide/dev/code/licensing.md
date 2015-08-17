---
title: License Considerations
layout: website-normal
---

The Apache Software Foundation, quite rightly, place a high standard on code provenance and license compliance. The
Apache license is flexible and compatible with many other types of license, meaning there is generally little problem
with incorporating other open source works into Brooklyn (with GPL being the notable exception). However diligence is
required to ensure that the project is legally sound, and third parties are rightfully credited where appropriate.

This page is an interpretation of the [Apache Legal Previously Asked Questions](http://www.apache.org/legal/resolved.html)
page as it specifically applies to the Brooklyn project, such as how we organise our code and the releases that we make.
However this page is not authoritative; if there is any conflict between this page and the Previously Asked Questions or
other Apache Legal authority, they will take precedence over this page.

If you have any doubt, please ask on the Brooklyn mailing list, and/or the Apache Legal mailing list.


What code licenses can we bundle?
---------------------------------

Apache Legal maintains the ["Category A" list](http://www.apache.org/legal/resolved.html#category-a), which is a list
of licenses that are compatible with the Apache License; that is, code under these licenses can be imported into
Brooklyn without violating Brooklyn's Apache License nor the code's original license (subject to correctly modifying
the `LICENSE` and/or `NOTICE` files; see below).

Apache Legal also maintain the ["Category X" list](http://www.apache.org/legal/resolved.html#category-x). Code licensed
under a Category X license **cannot** be imported into Brooklyn without violating either Brooklyn's Apache license or
the code's original license.

There is also a ["Category B" list](http://www.apache.org/legal/resolved.html#category-b), which are licenses that are
compatible with the Apache license only under certain circumstances. In practice, this means that we can declare a
dependency on a library licensed under a Category B license, and bundle the binary build of the library in our binary
builds, but we cannot import its source code into the Brooklyn codebase.

If the code you are seeking to import does not appear on any of these lists, check to see if the license content is the
same as a known license. For example, many projects actually use a BSD license but do not label it as "The BSD License".
If you are still not certain about the license, please ask on the Brooklyn mailing list, and/or the Apache Legal mailing
list.


About LICENSE and NOTICE files
------------------------------

Apache Legal requires that *each* artifact that the project releases contains a `LICENSE` and `NOTICE` file that is
*accurate for the contents of that artifact*. This means that, potentially, **every artifact that Brooklyn releases may
contain a different `LICENSE` and `NOTICE` file**. In practice, it's not usually that complicated and there are only a
few variations of these files needed.

Furthermore, *accurate* `LICENSE` and `NOTICE` files means that it correctly attributes the contents of the artifact,
and it does not contain anything unnecessary. This provision is what prevents us creating a mega LICENSE file and using
it in every single artifact we release, because in many cases it will contain information that is not relevant to an
artifact.

What is a correct `LICENSE` and `NOTICE` file?

* A correct `LICENSE` file is one that contains the text of the licence of any part of the code. The Apache Software
  License V2 will naturally be the first part of this file, as it's the license which we use for all the original code
  in Brooklyn. If some *Category A* licensed third-party code is bundled with this artifact, then the `LICENSE` file
  should identify what the third-party code is, and include a copy of its license. For example, if jquery is bundled
  with a web app, the `LICENSE` file would include a note jquery.js, its copyright and its license (MIT), and include a
  full copy of the MIT license.
* A correct `NOTICE` file contains notices required by bundled third-party code above and beyond that which we have
  already noted in `LICENSE`. In practice modifying `NOTICE` is rarely required beyond the initial note about Apache
  Brooklyn. See [What Are Required Third-party Notices?](http://www.apache.org/legal/resolved.html#required-third-party-notices)
  for more information


Applying LICENSE and NOTICE files to Brooklyn
---------------------------------------------

When the Brooklyn project makes a release, we produce and release the following types of artifacts:

1. One source release artifact
2. One binary release artifact
3. A large number of Maven release artifacts

Therefore, our source release, our binary release, and every one of our Maven release artifacts, must **each** have
their own, individually-tailored, `LICENSE` and `NOTICE` files.

To some extent, this is automated, using scripts in `usage/dist/licensing`;
but this must be manually run, and wherever source code is included or a project has insufficient information in its POM,
you'll need to add project-specific metadata (with a project-specific `source-inclusions.yaml` file and/or in the 
dist project's `overrides.yaml` file).  See the README.md in that project's folder for more information.

### Maven artifacts

Each Maven module will generally produce a JAR file from code under `src/main`, and a JAR file from code under
`src/test`. (There are some exceptions which may produce different artifacts.)

If the contents of the module are purely Apache Brooklyn original code, and the outputs are JAR files, then *no action
is required*. The default build process will incorporate a general-purpose `LICENSE` and `NOTICE` file into all built
JAR files. `LICENSE` will contain just a copy of the Apache Software License v2, and `NOTICE` will contain just the
module's own notice fragment.

However you will need to take action if either of these conditions are true:

* the module produces an artifact that is **not** a JAR file - for example, the jsgui project produces a WAR file;
* the module bundles third-party code that requires a change to `LICENSE` and/or `NOTICE`.

In this case you will need to disable the automatic insertion of `LICENSE` and `NOTICE` and insert your own versions
instead.

For an example of a JAR file with customized `LICENSE`/`NOTICE` files, refer to the `usage/cli` project.
For an example of a WAR file with customized `LICENSE`/`NOTICE` files, refer to the `usage/jsgui` project.

### The source release

In practice, the source release contains nothing that isn't in the individual produced Maven artifacts (the obvious
difference about it being source instead of binary isn't relevant). Therefore, the source release `LICENSE` and `NOTICE`
can be considered to be the union of every Maven artifact's `LICENSE` and `NOTICE`. The amalgamated files are kept in
the root of the repository.

### The binary release

This is the trickiest one to get right. The binary release includes everything that is in the source and Maven releases,
**plus every Java dependency of the project**. This means that the binary release is pulling in many additional items,
each of which have their own license, and which will therefore impact on `LICENSE` and `NOTICE`.

Therefore you must inspect every file that is present in the binary distribution, ascertain its license status, and
ensure that `LICENSE` and `NOTICE` are correct.

