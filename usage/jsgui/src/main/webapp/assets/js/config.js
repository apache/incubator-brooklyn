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
        "brooklyn":"libs/brooklyn",
        "brooklyn-utils":"libs/brooklyn-utils",
        "datatables-extensions":"libs/dataTables.extensions",
        "googlemaps":"view/googlemaps",
        "async":"libs/async",  //not explicitly referenced, but needed for google
        "text":"libs/text",
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
