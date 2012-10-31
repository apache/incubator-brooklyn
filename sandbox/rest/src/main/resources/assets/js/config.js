// set the require.js configuration for your application
require.config({

    baseUrl:"assets/js",

    paths:{
        // libraries
        "jquery":"libs/jquery-1.7.2",
        "underscore":"libs/underscore",
        "backbone":"libs/backbone",
        "bootstrap":"libs/bootstrap.min",
        "formatJson":"libs/json-formatter",
        "jquery-form":"libs/jquery.form",
        "tablesorter":"libs/jquery.dataTables.min",
        "async":"libs/async",
        "googlemaps":"libs/googlemaps",
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
