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

		$(function() {
		// setup ul.tabs to work as tabs for each div directly under div.panes
		$("ul.tabs").tabs("div.panes > div");
	});