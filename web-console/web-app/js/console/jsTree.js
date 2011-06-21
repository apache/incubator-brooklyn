OverPaas.jsTree = (function(parent) {

    function loadJstree() {
        $("#demo1")
            .jstree({
                "json_data" : {"ajax" : { "url" : OverPaas.urls.jsTree.loadJsTreeJson}},
                "plugins" : [ "themes", "json_data", "ui" ]
            });
    }

    return {
        loadJstree: loadJstree,
    };

}(OverPaas || {}));