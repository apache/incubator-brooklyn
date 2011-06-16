<html>
<head>
<title><g:layoutTitle default="Jhey's Test Console" /></title>
<g:javascript library="jquery/jquery" />
<g:javascript library="jquery/jquery.layout-1.2.0" />
<g:javascript library="jquery/jquery.jstree" />
<g:javascript library="jquery/jquery.tools.min" />
<link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
<link rel="stylesheet" type="text/css" href="http://static.flowplayer.org/tools/css/tabs.css" />
<script>
    $(document).ready(function () {
        $('body').layout({ applyDefaultStyles: true });
        $("body > .ui-layout-center").layout({applyDefaultStyles:true});
    });
</script>
<g:layoutHead />

</head>
<body>
<div class="ui-layout-center">
    <g:layoutBody />
</div>
<div class="ui-layout-north"> <g:render template="/shared/banner"/></div>
	<div class="ui-layout-south"><g:render template="/shared/footer"/></div>
</body>
</html>