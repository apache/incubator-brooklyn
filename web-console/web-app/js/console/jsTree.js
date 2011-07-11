Brooklyn.jsTree = (function(parent) {

    function loadJstree() {
        $("#jstree").jstree({
                "plugins" : [ "themes", "json_data", "ui" ],
                "json_data" : {
                    "ajax" : {
                        "url" : "jstree",
                        "data" : function () {
                            return {
                                //TODO Still need to link up UI component to corresponding parameters
                                "name" : $("#search-input").val().toLowerCase(),
                                "id" : "",
                                "applicationID" : ""
                            };
                        }
                    }
                }
            }).bind('select_node.jstree',
                    function(e, data){
                        var entity_id = $(data.rslt.obj).data('id')
                        $(Brooklyn.eventBus).trigger('entity_selected', entity_id);
                    });
    }

    function init() {
        $('#search-input').bind('input', loadJstree);
        $('#search-input').bind('search', loadJstree);
        $("#search-input").corner();
        loadJstree();
    }

    return {
        init: init
    };

}(Brooklyn || {}));

$(document).ready(Brooklyn.jsTree.init);
