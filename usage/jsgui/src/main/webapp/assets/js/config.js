/*
 * set the require.js configuration for your application
 */
require.config({
    /* Give 30s (default is 7s) in case it's a very poor slow network */
    waitSeconds:30,
    
    /* Libraries */
    baseUrl:"assets/js",
    paths:{
        "jquery":"libs/jquery",
        "underscore":"libs/underscore",
        "backbone":"libs/backbone",
        "bootstrap":"libs/bootstrap",
        "formatJson":"libs/json-formatter",
        "jquery-form":"libs/jquery.form",
        "jquery-datatables":"libs/jquery.dataTables",
        "jquery-slideto":"libs/jquery.slideto.min",
        "jquery-wiggle":"libs/jquery.wiggle.min",
        "jquery-ba-bbq":"libs/jquery.ba-bbq.min",
        "moment":"libs/moment.min",
        "handlebars":"libs/handlebars-1.0.rc.1",
        "brooklyn":"libs/brooklyn",
        "brooklyn-utils":"libs/brooklyn-utils",
        "datatables-extensions":"libs/dataTables.extensions",
        "googlemaps":"view/googlemaps",
        "async":"libs/async",  //not explicitly referenced, but needed for google
        "text":"libs/text",
        "uri":"libs/URI",
        "zeroclipboard":"libs/ZeroClipboard",
        
        "tpl":"../tpl"
    },
    
    shim:{
        "underscore":{
            exports:"_"
        },
        "backbone":{
            deps:[ "underscore", "jquery" ],
            exports:"Backbone"
        },
        "jquery-datatables": {
            deps: [ "jquery" ]
        },
        "datatables-extensions":{
            deps:[ "jquery", "jquery-datatables" ]
        },
        "jquery-form": { deps: [ "jquery" ] },
        "jquery-slideto": { deps: [ "jquery" ] },
        "jquery-wiggle": { deps: [ "jquery" ] },
        "jquery-ba-bbq": { deps: [ "jquery" ] },
        "handlebars": { deps: [ "jquery" ] },
        "bootstrap": { deps: [ "jquery" ] /* http://stackoverflow.com/questions/9227406/bootstrap-typeerror-undefined-is-not-a-function-has-no-method-tab-when-us */ }
    }
});

/*
 * Main application entry point.
 *
 * Inclusion of brooklyn module sets up logging.
 */
require([
    "backbone", "router", "brooklyn"
], function (Backbone, Router, Brooklyn) {
    var router = new Router();
    Backbone.history.start();
});
