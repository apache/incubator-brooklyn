---
layout: page
title: Test Jsonball
---

{% jsonball j from data { "a": "data" } %}
from data: j.a is {{ j.a }} (should be data)

{% assign v = '{ "a": "var" }' %}
{% jsonball j from var v %}
from var: j.a is {{ j.a }} (should be var)

{% jsonball j from file test_jsonball_file.json %}
from file: j.a is {{ j.a }} (should be file)

{% jsonball j from page test_jsonball_page.json %}
from page: j.a is {{ j.a }} (should be page)

