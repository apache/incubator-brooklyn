<!DOCTYPE html>
<html>
    <head>
        <link rel="stylesheet" href="${resource(dir:'css',file:'main.css')}" />
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
        <g:javascript library="application" />
        <g:javascript library="jquery/jquery" />
        <g:javascript library="jquery/jquery.cookie"/>
        <g:javascript library="jquery/jquery.hotkeys"/>
        <g:javascript library="jquery/jquery.jstree" />
        <g:javascript library="jquery/jquery.tools.min" />
       	<link rel="stylesheet" type="text/css" href="http://static.flowplayer.org/tools/css/tabs.css" />
		
	<script>
		$(function () {
			$("#demo1")
				.jstree({ 
					"json_data" : { "ajax" : { "url" : "<g:createLink controller='entity' action='jstree'/>"}},
					"plugins" : [ "themes", "json_data", "ui" ]
				})
				.bind("select_node.jstree", function (event, data) {
					 alert(data.rslt.obj.attr("id"));
				});

		});

		$(function() {
			$( "#tabs" ).tabs();
		});
	</script>
	
	<script>
	$(function() {
		// setup ul.tabs to work as tabs for each div directly under div.panes
		$("ul.tabs").tabs("div.panes > div");
	});
	</script>
	
    </head>
    <body>

	<div>
		<div id="demo1" style="float:left"></div>
	
		<ul class="tabs">
			<li><a href="#">Tab 1</a></li>
			<li><a href="#">Tab 2</a></li>
			<li><a href="#">Tab 3</a></li>
		</ul>
		
		<div class="panes" style="float:left">
			<div id ="entityName">First tab content. Tab contents are called "panes"</div>
			<div>Second tab content</div>
			<div>Third tab content</div>
		</div>
	</div>

	</body>
</html>