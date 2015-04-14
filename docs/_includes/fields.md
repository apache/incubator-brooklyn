
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

{% capture this_repo_base_url_content %}{{ apache_snapshots_repo_groupid_url }}{% endcapture %}
{% capture this_dist_url_list %}{{ this_repo_base_url_content }}/brooklyn-dist/{{ site.brooklyn-version }}/{% endcapture %}
{% capture this_alljar_url_list %}{{ this_repo_base_url_content }}/brooklyn-all/{{ site.brooklyn-version }}/{% endcapture %}

{% if SNAPSHOT %}
  {% capture this_dist_url_zip %}{{ this_repo_base_url_artifact }}?r=snapshots&g={{ brooklyn_group_id }}&a=brooklyn-dist&v={{ site.brooklyn-version }}&e=zip&c=dist{% endcapture %}
  {% capture this_dist_url_tgz %}{{ this_repo_base_url_artifact }}?r=snapshots&g={{ brooklyn_group_id }}&a=brooklyn-dist&v={{ site.brooklyn-version }}&e=tar.gz&c=dist{% endcapture %}
  {% capture this_alljar_url_jar %}{{ this_repo_base_url_artifact }}?r=snapshots&g={{ brooklyn_group_id }}&a=brooklyn-all&v={{ site.brooklyn-version }}&e=jar&c=with-dependencies{% endcapture %}
{% else %}<!--- RELEASE -->
  {% capture this_dist_url_zip %}{{ this_dist_url_list }}/brooklyn-dist-{{ site.brooklyn-version }}-dist.zip{% endcapture %}
  {% capture this_dist_url_tgz %}{{ this_dist_url_list }}/brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz{% endcapture %}
  {% capture this_alljar_url_jar %}{{ this_alljar_url_list }}/brooklyn-all-{{ site.brooklyn-version }}-with-dependencies.jar{% endcapture %}
{% endif %}

{% capture this_anything_url_search %}{{ this_repo_base_url_search }};gav~{{ brooklyn_group_id }}~~{{ site.brooklyn-version }}~~{% endcapture %}
{% capture this_dist_url_search %}{{ this_repo_base_url_search }};gav~{{ brooklyn_group_id }}~brooklyn-dist~{{ site.brooklyn-version }}~~{% endcapture %}
{% capture this_alljar_url_search %}{{ this_repo_base_url_search }};gav~{{ brooklyn_group_id }}~brooklyn-all~{{ site.brooklyn-version }}~~{% endcapture %}

<!-- OLD things -->

{% capture brooklyn_examples_branch %}{% if SNAPSHOT %}{{ site.brooklyn-snapshot-git-branch }}{% else %}{{ site.brooklyn-version }}{% endif %}{% endcapture %}

{% capture cloudsoft_snapshots_base_url %}http://developers.cloudsoftcorp.com/maven/snapshots/{% endcapture %}
{% capture cloudsoft_releases_base_url %}http://developers.cloudsoftcorp.com/maven/releases/{% endcapture %}
{% capture cloudsoft_this_version_base_url %}http://developers.cloudsoftcorp.com/maven/{% if SNAPSHOT %}snapshots/{% else %}releases/{% endif %}{% endcapture %}

{% capture cloudsoft_this_version_groupid_url %}{{ cloudsoft_this_version_base_url }}io/brooklyn/{% endcapture %}
{% capture cloudsoft_this_dist_url_dir %}{{ cloudsoft_this_version_groupid_url }}brooklyn-dist/{{ site.brooklyn-version }}/{% endcapture %}
{% capture cloudsoft_this_alljar_url_dir %}{{ cloudsoft_this_version_groupid_url }}brooklyn-all/{{ site.brooklyn-version }}/{% endcapture %}

<!--- both snapshots and releases -->
{% capture sonatype_repo_groupid_url %}https://oss.sonatype.org/content/groups/public/io/brooklyn/{% endcapture %}
<!--- releases --> 
{% capture mavencentral_repo_groupid_url %}http://repo1.maven.org/maven2/io/brooklyn/{% endcapture %}
