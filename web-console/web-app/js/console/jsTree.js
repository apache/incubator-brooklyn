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
                                "name" : $("#searchInput").val(),
                                "id" : "",
                                "applicationID" : ""
                            };
                        }
                    }
                }
            }
        );
    }

    return {
        loadJstree: loadJstree
    };

}(Brooklyn || {}));

$(document).ready(function(){
    Brooklyn.jsTree.loadJstree();
    $("#searchInput").corner();
});
