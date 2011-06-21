Brooklyn.jsTree = (function(parent) {

    function loadJstree() {
        $("#demo1")
            .jstree({
                "json_data" : {"ajax" : { "url" : Brooklyn.urls.jsTree.loadJsTreeJson}},
                "plugins" : [ "themes", "json_data", "ui" ]
            });
    }

    return {
        loadJstree: loadJstree,
    };

}(Brooklyn || {}));