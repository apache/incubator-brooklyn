<html>
    <head>
    <script type="text/javascript" src="${resource(dir:'dynjs', file:'jsTreeConfig')}"></script>
    <g:javascript src="console/jsTree.js" />
    <meta name="layout" content="main"></meta>

    <script>
        $(document).ready(Brooklyn.jsTree.loadJstree);
    </script>

    </head>
    <body>

	<div>
	    <!-- This is the tree -->
		<div id="demo1"></div>
	</div>
	</body>
</html>