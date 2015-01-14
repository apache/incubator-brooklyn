---
layout: website-normal
title: Site Map
---

<!-- TODO this is very much work in progress -->

<div class="sitemap">

{% assign visited = "" | split: "|" %}
{% assign site_items = "" | split: "|" %}
<ul>
{% for item in site.data.menu %}
  {% push site_items item %}
  {% include sitemap-item.html %}
{% endfor %}
</ul>
