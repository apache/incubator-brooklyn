---
layout: website-normal
title: Site Map
---

<!-- TODO this is very much work in progress -->

Site map is:

{% for item in site.data.menu %}
&nbsp;&nbsp;&nbsp; * {{ item['title_in_menu'] }} / {{ item.data['title'] }} - {{ item.data }}<br/>
  {% for item2 in item['menu'] %}
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; * {{ item2['title_in_menu'] }} / {{ item2['path'] }} / {{ item2['link'] }}<br/>
    {% for item3 in item2['menu'] %}
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; * {{ item3['title_in_menu'] }} / {{ item3['path'] }} / {{ item3['breadcrumbs'] }}<br/>
    {% endfor %}
  {% endfor %}
{% endfor %}

