Brooklyn.jsTree = (function(parent) {

    var currentTree;

    function getLatestTree(){
        $.getJSON("jstree?name=" + $("#search-input").val().toLowerCase(), refreshTreeIfChanged).error(
            function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get new entity data.");}
        );
    }

    function refreshTreeIfChanged(json){
        if(currentTree == null || json.toString() != currentTree.toString()){
            currentTree = json;
            loadJstree();
        }
    }

    function loadJstree() {
        $("#jstree").jstree({
                "plugins" : [ "themes", "json_data", "ui" ],
                "json_data" : {
                    data : currentTree
                }
            }).bind('select_node.jstree',
                    function(e, data){
                        var entity_id = $(data.rslt.obj).data('id')
                        $(Brooklyn.eventBus).trigger('entity_selected', entity_id);
                    });
    }

    function init() {
        $('#search-input').keyup(getLatestTree);
        $("#search-input").corner();
        $(Brooklyn.eventBus).bind("update", getLatestTree);
        getLatestTree();
    }

    return {
        init: init
    };

}(Brooklyn || {}));

$(document).ready(Brooklyn.jsTree.init);
