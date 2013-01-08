{% if site.brooklyn-version contains 'SNAPSHOT' %}{% capture SNAPSHOT %}true{% endcapture %}{% endif %}

{% capture maven_this_version_base_url %}http://developers.cloudsoftcorp.com/maven/{% if SNAPSHOT %}snapshots/{% else %}releases/{% endif %}{% endcapture %}
{% capture dist_url_dir %}{{ maven_this_version_base_url }}io/brooklyn/brooklyn-dist/{{ site.brooklyn-version }}{% endcapture %}
{% if SNAPSHOT %}{% else %}
  {% capture dist_url_zip %}{{ dist_url_dir }}/brooklyn-{{ site.brooklyn-version }}-dist.zip{% endcapture %}
  {% capture dist_url_zip %}{{ dist_url_tgz }}/brooklyn-{{ site.brooklyn-version }}-dist.tar.gz{% endcapture %}
{% endif %}
