Brooklyn.dashboard = (function(parent){
    function loadWidgets(){
        $( ".column" ).sortable({
			connectWith: ".column"
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

    var toggleContent = function(e){
	    var targetContent = $('div.itemContent', this.parentNode.parentNode);
	    if (targetContent.css('display') == 'none') {
		    targetContent.slideDown(300);
		    $(this).html('[-]');
	    } else {
		    targetContent.slideUp(300);
		    $(this).html('[+]');
	    }
	    return false;
    };

    function serialize(s){
	    serial = $.SortSerialize(s);
	    alert(serial.hash);
    }

    function init(){
        loadWidgets();
    }

    return{
        init: init
    };
})();
$(document).ready(Brooklyn.dashboard.init);
