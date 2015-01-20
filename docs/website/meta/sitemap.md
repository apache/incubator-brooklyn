---
layout: website-normal
title: Site Map
---

<div class="sitemap">

<div class="search_right">
<form method="get" id="simple_google" class="searchform" action="http://www.google.com/search">
                <input type="text" class="searchinput" name="brooklyn-search" placeholder="Google site search: type &amp; hit enter">
                <input type="hidden" name="q" value="">
            </form>
</div>

{% assign visited = "" | split: "|" %}
{% assign site_items = "" | split: "|" %}
<ul>
{% for item in site.data.menu %}
  {% push site_items item %}
  {% include sitemap-item.html %}
{% endfor %}
</ul>

</div>

