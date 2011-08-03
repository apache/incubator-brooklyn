//*** This JS File is for defining the top level layout for the brooklyn web console ***//

$(document).ready(function () {
     $("body").layout({ applyDefaultStyles:true });
     //the option set in the master tab means that everytime the user selects a tab, we relayout the tabs content.
     $("#master-tabs").tabs({
     show: function (evt, ui) {
					var tabLayout = $(ui.panel).data("layout");
					if ( tabLayout ) tabLayout.resizeAll();
		            var pageLayout = $("body").data("layout");
		            if (pageLayout) pageLayout.resizeAll();
				}

     
     });
    //finally layout each master tab. Can't do this by class for some reason?
    $("#detail").layout({ applyDefaultStyles:true });

});


