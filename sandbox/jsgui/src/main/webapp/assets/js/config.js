// set the require.js configuration for your application
require.config({

    baseUrl:"assets/js",

    paths:{
        // libraries
        "jquery":"libs/jquery",
        "underscore":"libs/underscore",
        "backbone":"libs/backbone",
        "bootstrap":"libs/bootstrap",
        "formatJson":"libs/json-formatter",
        "jquery-form":"libs/jquery.form",
        "tablesorter":"libs/jquery.dataTables",
        "async":"libs/async",
        "jquery-slideto":"libs/jquery.slideto.min",
        "jquery-wiggle":"libs/jquery.wiggle.min",
        "jquery-ba-bbq":"libs/jquery.ba-bbq.min",
        "handlebars":"libs/handlebars-1.0.rc.1",

        "googlemaps":"view/googlemaps",
        "text":"libs/text",
        "tpl":"../tpl"
    },

    shim:{
        underscore:{
            exports:"_"
        },
        formatJson:{
            exports:"FormatJSON"
        },
        backbone:{
            deps:[ "underscore", "jquery"],
            exports:"Backbone"
        }
    }
})

/**
 * Main application entry point.
 */
require([
    "backbone", "router"
], function (Backbone, Router) {
    var router = new Router();
    Backbone.history.start();
})
