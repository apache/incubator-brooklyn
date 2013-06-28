({
    name: "config",
    baseUrl: ".",
    paths: {
        // Must be included!
        "requireLib": "libs/require",

        // From config.js
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
        "async":"libs/async",
        "autolink": "libs/autolink",
        "text":"libs/text",
        "tpl":"../tpl"
    },
    include: ["requireLib"],
    mainConfigFile: "config.js",
    out: "jsgui.js"
})
