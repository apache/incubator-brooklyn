<!DOCTYPE html>
<html>
    <head>

    <meta name="layout" content="main">  </meta>
		
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
	    <!-- This is the tree -->
		<div id="demo1"></div>

		<ul class="tabs">
			<li><a href="#">Tab 1</a></li>
			<li><a href="#">Tab 2</a></li>
			<li><a href="#">Tab 3</a></li>
		</ul>
		
		<div class="panes">
			<div id ="entityName">First tab content. Tab contents are called "panes"</div>
			<div>Second tab content</div>
			<div>Third tab content</div>

		</div>
	</div>
	</body>
</html>