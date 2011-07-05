/* Used to bind jQuery custom events for the application.
 *
 * TODO: Move someone more generic. 
*/
var eventBus = {};

Brooklyn.jsTree = (function(parent) {

    function loadJstree() {
        $("#demo1")
            .jstree({
                "plugins" : [ "themes", "json_data", "ui" ],
                "json_data" : {
                    "ajax" : {
                        "url" : Brooklyn.urls.jsTree.loadJsTreeJson,
                        "data" : function () {
                            return {
                                //TODO Still need to link up UI component to corresponding parameters
                                "name" : $("#searchInput").val().toLowerCase(),
                                "id" : "",
                                "applicationID" : ""
                            };
                        }
                    }
                }
            }).bind('select_node.jstree',
                    function(e, data){
                        var entity_id = $(data.rslt.obj).data('id')
                        $(eventBus).trigger('entity_selected', entity_id);
                    });
    }

    return {
        loadJstree: loadJstree
    };

}(Brooklyn || {}));

$(document).ready(function(){
    Brooklyn.jsTree.loadJstree();
    $("#searchInput").corner();
});
