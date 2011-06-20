var OverPaas = (function(parent) {

    var urls = parent.urls = (parent.urls || {});

    urls.jsTree = {
		loadJsTreeJson : "<g:createLink controller='entity' action='jstree'/>"
    };

    return parent;

}(OverPaas || {}));