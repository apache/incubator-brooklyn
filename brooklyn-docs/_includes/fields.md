
{% if site.brooklyn-version contains 'SNAPSHOT' %}{% capture SNAPSHOT %}true{% endcapture %}{% endif %}

{% capture brooklyn_properties_url_path %}{{ site.path.guide }}/start/brooklyn.properties{% endcapture %}
{% capture brooklyn_properties_url_live %}{{ site.url_root }}{{ brooklyn_properties_url_path }}{% endcapture %}

{% capture brooklyn_group_id %}org.apache.brooklyn{% endcapture %}
{% capture brooklyn_group_id_path %}org/apache/brooklyn{% endcapture %}

{% capture this_repo_base_url %}https://repository.apache.org{% endcapture %}
{% capture this_repo_base_url_search %}{{ this_repo_base_url }}/index.html#nexus-search{% endcapture %}
{% capture this_repo_base_url_artifact %}{{ this_repo_base_url }}/service/local/artifact/maven/redirect{% endcapture %}

{% capture apache_snapshots_repo_groupid_url %}{{ this_repo_base_url }}/content/repositories/snapshots/{{ brooklyn_group_id_path }}{% endcapture %}
{% capture apache_releases_repo_groupid_url %}{{ this_repo_base_url }}/content/repositories/releases/{{ brooklyn_group_id_path }}{% endcapture %}

{% capture this_repo_base_url_content %}{% if SNAPSHOT %}{{ apache_snapshots_repo_groupid_url }}{% else %}{{ apache_releases_repo_groupid_url }}{% endif %}{% endcapture %}
{% capture this_dist_url_list %}{{ this_repo_base_url_content }}/brooklyn-dist/{{ site.brooklyn-version }}/{% endcapture %}

{% if SNAPSHOT %}
  <!-- put e field last, so we can append .sha1 -->
  {% capture this_dist_url_zip %}{{ this_repo_base_url_artifact }}?r=snapshots&g={{ brooklyn_group_id }}&a=brooklyn-dist&v={{ site.brooklyn-version }}&c=dist&e=zip{% endcapture %}
  {% capture this_dist_url_tgz %}{{ this_repo_base_url_artifact }}?r=snapshots&g={{ brooklyn_group_id }}&a=brooklyn-dist&v={{ site.brooklyn-version }}&c=dist&e=tar.gz{% endcapture %}
{% else %}<!--- RELEASE -->
  {% capture this_dist_url_zip %}{{ this_dist_url_list }}/brooklyn-dist-{{ site.brooklyn-version }}-dist.zip{% endcapture %}
  {% capture this_dist_url_tgz %}{{ this_dist_url_list }}/brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz{% endcapture %}
{% endif %}

{% capture this_anything_url_search %}{{ this_repo_base_url_search }};gav~{{ brooklyn_group_id }}~~{{ site.brooklyn-version }}~~{% endcapture %}
{% capture this_dist_url_search %}{{ this_repo_base_url_search }};gav~{{ brooklyn_group_id }}~brooklyn-dist~{{ site.brooklyn-version }}~~{% endcapture %}


