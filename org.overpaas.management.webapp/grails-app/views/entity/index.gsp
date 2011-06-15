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
		
    </head>
    <body>

	<div id="demo1">
	</div>
	
	<script>
		$(function () {
			$("#demo1").jstree({ 
				"json_data" : {
					"ajax" : {
						"url" : "<g:createLink controller='entity' action='list'/>",
						"data" : function (n) { 
							return { "this$0" : null }; 
						}
					}
				},
				"plugins" : [ "themes", "json_data" ]
			});
		});
		</script>
	</body>
</html>