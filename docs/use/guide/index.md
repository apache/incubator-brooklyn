---
layout: page
title: User Guide
toc: ../../toc.json
categories: use
---

TODO -- would like to have PDF avail, with links in corner -- "download PDF"

## Table of Contents

{% capture ugtocs %}{% readj toc.json %}{% endcapture %}
{% jsonball ugtoc from var ugtocs %}

<div id="ug_toc_lists">
<ul>
{% for x in ugtoc %}
	<li><a class='toc' href="{{ x.file }}">{{ x.title }}</a></li>
	{% if x.children %}
		<ul>
		{% for x2 in x.children %}
			<li><a class='toc' href="{{ x2.file }}">{{ x2.title }}</a></li>
			{% if x2.children %}
				<ul>
				{% for x3 in x2.children %}
					<li><a class='toc' href="{{ x3.file }}">{{ x3.title }}</a></li>
				{% endfor %}
				</ul>
			{% endif %}
		{% endfor %}
		</ul>
	{% endif %}
{% endfor %} 
</ul>
</div>


<div id="ug_toc">
    <div id="accordionish">
{% for x in ugtoc %}
  {% if x.children %}
      <div class="accordiable toc1 {% if page.url == x.file %}toc-active{% endif %}"><a href="{{ x.file }}">{{ x.title }}</a></div>
          <div>
    {% for x2 in x.children %}
      {% if x2.children %}
        <div class="accordiable toc2 {% if page.url == x2.file %}toc-active{% endif %}"><a href="{{ x2.file }}">{{ x2.title }}</a></div>
            <div>
        {% for x3 in x2.children %}
          <div class="unaccordiable toc3 {% if page.url == x3.file %}toc-active{% endif %}"><a href="{{ x3.file }}">{{ x3.title }}</a></div>
        {% endfor %}
            </div>
      {% else %}
        <div class="unaccordiable toc2 {% if page.url == x2.file %}toc-active{% endif %}"><a href="{{ x2.file }}">{{ x2.title }}</a></div>
      {% endif %}
    {% endfor %}
          </div>
  {% else %}
      <div class="unaccordiable toc1 {% if page.url == x.file %}toc-active{% endif %}"><a href="{{ x.file }}">{{ x.title }}</a></div>
  {% endif %}
{% endfor %} 
</div>
