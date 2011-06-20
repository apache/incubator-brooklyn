<html>
<head>
<meta name="layout" content="test"></meta>

</head>

  <body>
    <!-- TABS CONTAINER -->
    <div class="ui-layout-center" style="padding:0px">
       <ul class="tabs">
			<li><a href="#">Summary</a></li>
			<li><a href="#">Locations</a></li>
			<li><a href="#">Health</a></li>
			<li><a href="#">Structure</a></li>
	   </ul>
	   <div class="panes">
			<div id ="entityName">First tab content. Tab contents are called "panes"</div>
			<div>Second tab content</div>
			<div>Third tab content</div>
			<div>Fourth tab content</div>
	   </div>
    </div>
    <!-- TREE CONTAINER -->
	<div class="ui-layout-west" >
	    <div class="ui-layout-north">
	        <!--//TODO: configure filter logic for jstree, look at jstree docs.-->
	        <div id="filterbar"><input type="text"></input><button>Filter</button></div>
	    </div>
	    <div class="ui-layout-center">
	        <!-- This is the tree -->
		    <div id="demo1" style="width:0%"></div>
	    </div>
	</div>
  </body>
</html>
