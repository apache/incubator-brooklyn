Brooklyn.dashboard = (function(parent){
    function loadWidgets(){
        $( ".column" ).sortable({
			connectWith: ".column",
            handle: ".portlet-header",
            cursor: "crosshair"
		});
        $( ".master-column-wrapper" ).sortable({
			connectWith: ".master-column-wrapper",
            handle: ".portlet-header",
            cursor: "crosshair"
		});
		$( ".portlet" ).addClass( "ui-widget ui-widget-content ui-helper-clearfix ui-corner-all" )
			.find( ".portlet-header" )
				.addClass( "ui-widget-header ui-corner-all" )
				.prepend( "<span class='ui-icon ui-icon-minusthick'></span>")
				.end()
			.find( ".portlet-content" );
		$( ".portlet-header .ui-icon" ).click(function() {
			$( this ).toggleClass( "ui-icon-minusthick" ).toggleClass( "ui-icon-plusthick" );
			$( this ).parents( ".portlet:first" ).find( ".portlet-content" ).toggle();
		});
		$( ".column" ).disableSelection();
    }
    function init(){
        loadWidgets();
    }

    return{
        init: init
    };
})();
$(document).ready(Brooklyn.dashboard.init);
