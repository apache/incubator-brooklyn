
//TABS WIDGET.

$(function() {
			$( "#tabs" ).tabs();
	});
$(function() {
		// setup ul.tabs to work as tabs for each div directly under div.panes
		$("ul.tabs").tabs("div.panes > div");
});

//INITIALISE TREE
$(document).ready(OverPaas.jsTree.loadJstree);