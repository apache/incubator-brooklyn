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
                                "name" : "",
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

$(document).ready(Brooklyn.jsTree.loadJstree);