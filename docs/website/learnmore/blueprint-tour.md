---
layout: website-normal
title: A Quick Tour of a Brooklyn Blueprint
title_in_menu: Blueprint Tour
---

<div class="jumobotron annotated_blueprint" markdown="1">
  <div class="code_scroller">
    <div class="initial_notice"><div><div>
      Hover over an element to learn more
      <div class="ann_light">This message will go away in <span id="countdown">3s</span></div>
      <div class="ann_play fa fa-play-circle-o"></div>
    </div></div></div>
    <div class="code_viewer">
  
<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Describe your application
        </div>
        <div class="long"><p>
            Start by giving it a name, 
            optionally adding a version and other metadata.
            The format is YAML -- a human-friendly extension to JSON --
            following the  
            <a href="{{ site.path.website }}/learnmore/theory.html#standards">CAMP</a> standard.
          </p><p>
            Treat it like source code: use comments, version control it, test it with CI.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div><span class="ann_highlight"># java chatroom with ruby chatbot and couchbase backend (example)</span>    
name: Chatroom with Chatbot
services:</div></div>
    
<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Compose blueprints
        </div>
        <div class="long"><p>
            Choose your building blocks from a large curated catalog,  
            and compose them together to form new blueprints
            you can deploy and share.
          </p><p>
            Customize with config keys, such as the initial size
            and, for Couchbase, the data buckets required.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div><span class="ann_highlight">- type: couchbase-cluster</span>
  initialSize: 3
  createBuckets: [{ bucket: chatroom }]
  id: chat-couchbase</div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Run scripts and recipes
        </div>
        <div class="long"><p>
            Use bash, with variables supplied by Brooklyn;
            or Chef recipes, with attributes passed from config;
            or package managers, dockerfiles, etc.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>- type: bash-server
  launch.command: |
<span class="ann_highlight">    wget http://example.com/couchbase-chat/chat-bot/{server.rb,Gemfile,install_ruby_and_libs.sh}
    bash install_ruby_and_libs.sh
    ruby ./server.rb $COUCHBASE_URL</span></div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Inject dependencies
        </div>
        <div class="long"><p>
            Connect entities with each other using 
            <i>sensors</i> published at runtime to give
            just-in-time resolution for
            shell variables, template expansion, REST calls,
            and any other "happens-before" or "on-change" behaviour.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>  shell.env:
    COUCHBASE_URL:
<span class="ann_highlight">      $brooklyn:entity("chat-couchbase").
        attributeWhenReady("couchbase.cluster.connection.url")</span></div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Configure locations
        </div>
        <div class="long"><p>
            Give generic VM properties or specific images and flavors.
            Networking topologies and geographic constraints are also supported.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>  provisioning.properties:
<span class="ann_highlight">    osFamily: ubuntu
    minRam: 4gb</span>
</div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Extend using Java
        </div>
        <div class="long"><p>
            Create new entities, policies, and "effector" operations
            using Java or JVM bridges to many languages, workflow systems,
            or PaaSes.
          </p><p>
            Add new blueprints to the catalog, dynamically,
            with versions and libraries handled 
            under the covers automatically with OSGi.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>- type: <span class="ann_highlight">org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster:1.1.0</span>
  war: http://example.com/couchbase-chat/chatroom.war
  java.sysprops:
    chat.db.url: $brooklyn:entity("chat-couchbase").attributeWhenReady("couchbase.cluster.connection.url")</div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Attach management logic
        </div>
        <div class="long"><p>
          Set up policies which subscribe to real-time metric sensors
          to scale, throttle, failover, or follow-the-{sun,moon,action,etc}.
          Cloud should be something that <i>applications</i> consume, not people!
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>  brooklyn.policies:
  - type: <span class="ann_highlight">autoscaler</span>
    brooklyn.config:
      metric: $brooklyn:sensor("webapp.reqs.perSec.windowed.perNode")
      metricLowerBound: 400
      metricUpperBound: 600</div></div>

<div class="block">
      <div class="annotations_wrapper1"><div class="annotations_wrapper2"><div class="annotations">
        <div class="short">
          Run across many locations
        </div>
        <div class="long"><p>
          Blueprints are designed for portability.
          Pick from dozens of clouds in hundreds of datacenters. 
          Or machines with fixed IP addresses, localhost, 
          Docker on <a href="http://clocker.io">Clocker</a>, etc.
        </p><p>
          And you're not limited to servers:
          services, PaaS, even networks can be locations.
        </p></div>
      </div><div class="connector"><div>&nbsp;</div></div></div></div>
<div>location:
  <span class="ann_highlight">jclouds:aws-ec2</span>:
    region: us-east-1
    identity: <i>AKA_YOUR_ACCESS_KEY_ID</i>
    credential: <i>[access-key-hex-digits]</i></div></div>

  </div></div>
</div>

<script language="JavaScript" type="application/javascript">

{% comment %}
I've done all I could manage with pure CSS. Just one thing, the bg color
on hover doesn't apply full width to the row if it extends the code_scroller.
Fix it with JS. 

Also resize the warning to be full size, and after first hover get rid of it.
{% endcomment %} 

$(function() {
  maxCodeWidth = Math.max.apply(Math, $(".annotated_blueprint div.block > div:last-child").map(function(){ return this.scrollWidth; }).get());
  $(".annotated_blueprint div.block").width(maxCodeWidth);
})

$(".annotated_blueprint .code_scroller .initial_notice > div").height($(".annotated_blueprint .code_scroller .code_viewer").height());
$(".annotated_blueprint .code_scroller .initial_notice > div").width($(".annotated_blueprint .code_scroller").width());
$(".annotated_blueprint .code_scroller").hover(function() {
  $(".annotated_blueprint .initial_notice").css("display", "none");
});
$(function() {
  setTimeout(function() { $(".annotated_blueprint .initial_notice").hide(400); }, 3000);
  setTimeout(function() { $(".annotated_blueprint #countdown").text("2s"); }, 1000);
  setTimeout(function() { $(".annotated_blueprint #countdown").text("1s"); }, 2000);
});
</script>
