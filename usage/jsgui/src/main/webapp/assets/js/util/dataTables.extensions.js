/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * ---
 *
 * This code has been created by the Apache Brooklyn contributors.
 * It is heavily based on earlier software but rewritten for clarity 
 * and to preserve license integrity.
 *
 * This work is based on the existing jQuery DataTables plug-ins for:
 *
 * * fnStandingRedraw by Jonathan Hoguet, 
 *   http://www.datatables.net/plug-ins/api/fnStandingRedraw
 *
 * * fnProcessingIndicator by Allan Chappell
 *   https://www.datatables.net/plug-ins/api/fnProcessingIndicator
 *
 */
define([
    "jquery", "jquery-datatables"
], function($, dataTables) {

$.fn.dataTableExt.oApi.fnStandingRedraw = function(oSettings) {
    if (oSettings.oFeatures.bServerSide === false) {
        // remember and restore cursor position
        var oldDisplayStart = oSettings._iDisplayStart;
        oSettings.oApi._fnReDraw(oSettings);
        oSettings._iDisplayStart = oldDisplayStart;
        oSettings.oApi._fnCalculateEnd(oSettings);
    }
    // and force draw
    oSettings.oApi._fnDraw(oSettings);
};


jQuery.fn.dataTableExt.oApi.fnProcessingIndicator = function(oSettings, bShow) {
    if (typeof bShow === "undefined") bShow=true;
    this.oApi._fnProcessingDisplay(oSettings, bShow);
};

});
