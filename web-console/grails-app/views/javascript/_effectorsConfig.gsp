var Brooklyn = (function(parent) {

    var urls = parent.urls = (parent.urls || {});

    urls.effectors = {
		getEffectors : "<g:createLink controller='entity' action='effectors'/>"
    };

    return parent;

}(Brooklyn || {}));