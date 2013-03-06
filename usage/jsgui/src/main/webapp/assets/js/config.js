/*
 * set the require.js configuration for your application
 */
require.config({

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
        "handlebars":"libs/handlebars-1.0.rc.1",
        "brooklyn-utils":"libs/brooklyn-utils",
        "datatables-extensions":"libs/dataTables.extensions",
        "googlemaps":"view/googlemaps",
        "text":"libs/text",
//        "async":"libs/async",
        "tpl":"../tpl"
    },
    shim:{
        "underscore":{
            exports:"_"
        },
        "formatJson":{
            exports:"FormatJSON"
        },
        "backbone":{
            deps:[ "underscore", "jquery" ],
            exports:"Backbone"
        },
        "datatables-extensions":{
            deps:[ "jquery", "jquery-datatables" ]
        }
    }
});

/*
 * Main application entry point.
 */
require([
    "backbone", "router"
], function (Backbone, Router) {
    var router = new Router();
    Backbone.history.start();
});
