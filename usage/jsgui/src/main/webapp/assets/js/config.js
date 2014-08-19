/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
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
        "brooklyn-view":"libs/brooklyn-view",
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
    "backbone", "router", "brooklyn", "model/ha"
], function (Backbone, Router, Brooklyn, ha) {
    ha.autoUpdate();
    var router = new Router();
    Backbone.history.start();
});
