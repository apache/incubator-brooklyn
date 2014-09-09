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
/**
 * Render entity config tab.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "zeroclipboard", "view/viewutils", 
    "model/config-summary", "text!tpl/apps/config.html", "text!tpl/apps/config-name.html",
    "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Util, ZeroClipboard, ViewUtils, ConfigSummary, ConfigHtml, ConfigNameHtml) {

    // TODO consider extracting all such usages to a shared ZeroClipboard wrapper?
    ZeroClipboard.config({ moviePath: 'assets/js/libs/ZeroClipboard.swf' });

    var configHtml = _.template(ConfigHtml),
        configNameHtml = _.template(ConfigNameHtml);

    // TODO refactor to share code w entity-sensors.js
    // in meantime, see notes there!
    var EntityConfigView = Backbone.View.extend({
        template: configHtml,
        configMetadata:{},
        refreshActive:true,
        zeroClipboard: null,
        
        events:{
            'click .refresh':'updateConfigNow',
            'click .filterEmpty':'toggleFilterEmpty',
            'click .toggleAutoRefresh':'toggleAutoRefresh',

            'mouseup .valueOpen':'valueOpen',
            'mouseover #config-table tbody tr':'noteFloatMenuActive',
            'mouseout #config-table tbody tr':'noteFloatMenuSeemsInactive',
            'mouseover .floatGroup':'noteFloatMenuActive',
            'mouseout .floatGroup':'noteFloatMenuSeemsInactive',
            'mouseover .clipboard-item':'noteFloatMenuActiveCI',
            'mouseout .clipboard-item':'noteFloatMenuSeemsInactiveCI',
            'mouseover .hasFloatLeft':'showFloatLeft',
            'mouseover .hasFloatDown':'enterFloatDown',
            'mouseout .hasFloatDown':'exitFloatDown',
            'mouseup .light-popup-menu-item':'closeFloatMenuNow',
        },
        
        initialize:function () {
            _.bindAll(this);
            this.$el.html(this.template());
            
            var that = this,
                $table = this.$('#config-table');
            that.table = ViewUtils.myDataTable($table, {
                "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                    $(nRow).attr('id', aData[0]);
                    $('td',nRow).each(function(i,v){
                        if (i==1) $(v).attr('class','config-value');
                    });
                    return nRow;
                },
                "aoColumnDefs": [
                                 { // name (with tooltip)
                                     "mRender": function ( data, type, row ) {
                                         var actions = that.getConfigActions(data.name);
                                         var context = _.extend(data, { 
                                             description: data['description'], type: data['type']});
                                         return configNameHtml(context);
                                     },
                                     "aTargets": [ 1 ]
                                 },
                                 { // value
                                     "mRender": function ( data, type, row ) {
                                         var escapedValue = Util.toDisplayString(data);
                                         if (type!='display')
                                             return escapedValue;
                                         
                                         var hasEscapedValue = (escapedValue!=null && (""+escapedValue).length > 0);
                                             configName = row[0],
                                             actions = that.getConfigActions(configName);
                                         
                                         // datatables doesn't seem to expose any way to modify the html in place for a cell,
                                         // so we rebuild
                                         
                                         var result = "<span class='value'>"+(hasEscapedValue ? escapedValue : '')+"</span>";
                                         if (actions.open)
                                             result = "<a href='"+actions.open+"'>" + result + "</a>";
                                         if (escapedValue==null || escapedValue.length < 3)
                                             // include whitespace so we can click on it, if it's really small
                                             result += "&nbsp;&nbsp;&nbsp;&nbsp;";

                                         var $row = $('tr[id="'+configName+'"]');
                                         var existing = $row.find('.dynamic-contents');
                                         // for the json url, use the full url (relative to window.location.href)
                                         var jsonUrl = actions.json ? new URI(actions.json).resolve(new URI(window.location.href)).toString() : null;
                                         // prefer to update in place, so menus don't disappear, also more efficient
                                         // (but if menu is changed, we do recreate it)
                                         if (existing.length>0) {
                                             if (that.checkFloatMenuUpToDate($row, actions.open, '.actions-open', 'open-target') &&
                                                 that.checkFloatMenuUpToDate($row, escapedValue, '.actions-copy') &&
                                                 that.checkFloatMenuUpToDate($row, actions.json, '.actions-json-open', 'open-target') &&
                                                 that.checkFloatMenuUpToDate($row, jsonUrl, '.actions-json-copy', 'copy-value')) {
//                                                 log("updating in place "+configName)
                                                 existing.html(result);
                                                 return $row.find('td.config-value').html();
                                             }
                                         }
                                         
                                         // build the menu - either because it is the first time, or the actions are stale
//                                         log("creating "+configName);
                                         
                                         var downMenu = "";
                                         if (actions.open)
                                             downMenu += "<div class='light-popup-menu-item valueOpen actions-open' open-target='"+actions.open+"'>" +
                                                    "Open</div>";
                                         if (hasEscapedValue) downMenu +=
                                             "<div class='light-popup-menu-item handy valueCopy actions-copy clipboard-item'>Copy Value</div>";
                                         if (actions.json) downMenu +=
                                             "<div class='light-popup-menu-item handy valueOpen actions-json-open' open-target='"+actions.json+"'>" +
                                                 "Open REST Link</div>";
                                         if (actions.json && hasEscapedValue) downMenu +=
                                             "<div class='light-popup-menu-item handy valueCopy actions-json-copy clipboard-item' copy-value='"+
                                                 jsonUrl+"'>Copy REST Link</div>";
                                         if (downMenu=="") {
//                                             log("no actions for "+configName);
                                             downMenu += 
                                                 "<div class='light-popup-menu-item'>(no actions)</div>";
                                         }
                                         downMenu = "<div class='floatDown'><div class='light-popup'><div class='light-popup-body'>"
                                             + downMenu +
                                             "</div></div></div>";
                                         result = "<span class='hasFloatLeft handy dynamic-contents'>" + result +
                                                "</span>" +
                                                "<div class='floatLeft'><span class='icon-chevron-down hasFloatDown'></span>" +
                                                downMenu +
                                                "</div>";
                                         result = "<div class='floatGroup'>" + result + "</div>";
                                         // also see updateFloatMenus which wires up the JS for these classes
                                         
                                         return result;
                                     },
                                     "aTargets": [ 2 ]
                                 },
                                 // ID in column 0 is standard (assumed in ViewUtils)
                                 { "bVisible": false,  "aTargets": [ 0 ] }
                             ]            
            });
            
            this.zeroClipboard = new ZeroClipboard();
            this.zeroClipboard.on( "dataRequested" , function(client) {
                var text = $(this).attr('copy-value');
                if (!text) text = $(this).closest('.floatGroup').find('.value').html();
                try {
//                    log("Copying text '"+text+"' to clipboard");
                    client.setText(text);
                    
                    var $widget = $(this);
                    var oldHtml = $widget.html();
                    var fnRestore = _.once(function() { $widget.html(oldHtml); });
                    // show the word copied for feedback;
                    // NB this occurs on mousedown, due to how flash plugin works
                    // (same style of feedback and interaction as github)
                    // the other "clicks" are now triggered by *mouseup*
                    $widget.html('<b>Copied!</b>');
                    setTimeout(fnRestore, 3000);
                    
                    // these listeners stay registered until page is reloaded
                    // but they do nothing after first run, due to use of _.once
                    // however the timeout is good enough, and actually desired
                    // because on corner case of mousedown-moveaway-mouseup,
                    // we want to keep the feedback; so they work, but are disabled for now.
                    // (remove once we are happy with this behaviour, since Feb 2014)
//                    that.zeroClipboard.on( "mouseout", fnRestore);
//                    that.zeroClipboard.on( "mouseup", fnRestore);
                } catch (e) {
                    log("Zeroclipboard failure; falling back to prompt mechanism");
                    log(e);
                    Util.promptCopyToClipboard(text);
                }
            });
            // these seem to arrive delayed sometimes, so we also work with the clipboard-item class events
            this.zeroClipboard.on( "mouseover", function() { that.noteFloatMenuZeroClipboardItem(true, this); } );
            this.zeroClipboard.on( "mouseout", function() { that.noteFloatMenuZeroClipboardItem(false, this); } );
            this.zeroClipboard.on( "mouseup", function() { that.closeFloatMenuNow(); } );

            ViewUtils.addFilterEmptyButton(this.table);
            ViewUtils.addAutoRefreshButton(this.table);
            ViewUtils.addRefreshButton(this.table);
            this.loadConfigMetadata();
            this.updateConfigPeriodically();
            this.toggleFilterEmpty();
            return this;
        },

        beforeClose: function () {
            if (this.zeroClipboard) {
                this.zeroClipboard.destroy();
            }
        },

        floatMenuActive: false,
        lastFloatMenuRowId: null,
        lastFloatFocusInTextForEventUnmangling: null,
        updateFloatMenus: function() { this.zeroClipboard.clip( $('.valueCopy') ); },
        showFloatLeft: function(event) {
            this.noteFloatMenuFocusChange(true, event, "show-left");
            this.showFloatLeftOf($(event.currentTarget));
        },
        showFloatLeftOf: function($hasFloatLeft) {
            $hasFloatLeft.next('.floatLeft').show(); 
        },
        enterFloatDown: function(event) {
            this.noteFloatMenuFocusChange(true, event, "show-down");
//            log("entering float down");
            var fdTarget = $(event.currentTarget);
//            log( fdTarget );
            this.floatDownFocus = fdTarget;
            var that = this;
            setTimeout(function() {
                that.showFloatDownOf( fdTarget );
            }, 200);
        },
        exitFloatDown: function(event) {
//            log("exiting float down");
            this.floatDownFocus = null;
        },
        showFloatDownOf: function($hasFloatDown) {
            if ($hasFloatDown != this.floatDownFocus) {
//                log("float down did not hover long enough");
                return;
            }
            var down = $hasFloatDown.next('.floatDown');
            down.show();
            $('.light-popup', down).show(2000); 
        },
        noteFloatMenuActive: function(focus) { 
            this.noteFloatMenuFocusChange(true, focus, "menu");
            
            // remove dangling zc events (these don't always get removed, apparent bug in zc event framework)
            // this causes it to flash sometimes but that's better than leaving the old item highlighted
            if (focus.toElement && $(focus.toElement).hasClass('clipboard-item')) {
                // don't remove it
            } else {
                var zc = $(focus.target).closest('.floatGroup').find('div.zeroclipboard-is-hover');
                zc.removeClass('zeroclipboard-is-hover');
            }
        },
        noteFloatMenuSeemsInactive: function(focus) { this.noteFloatMenuFocusChange(false, focus, "menu"); },
        noteFloatMenuActiveCI: function(focus) { this.noteFloatMenuFocusChange(true, focus, "menu-clip-item"); },
        noteFloatMenuSeemsInactiveCI: function(focus) { this.noteFloatMenuFocusChange(false, focus, "menu-clip-item"); },
        noteFloatMenuZeroClipboardItem: function(seemsActive,focus) { 
            this.noteFloatMenuFocusChange(seemsActive, focus, "clipboard");
            if (seemsActive) {
                // make the table row highlighted (as the default hover event is lost)
                // we remove it when the float group goes away
                $(focus).closest('tr').addClass('zeroclipboard-is-hover');
            } else {
                // sometimes does not get removed by framework - though this doesn't seem to help
                // as you can see by logging this before and after:
//                log(""+$(focus).attr('class'))
                // the problem is that the framework seems sometime to trigger this event before adding the class
                // see in noteFloatMenuActive where we do a different check
                $(focus).removeClass('zeroclipboard-is-hover');
            }
        },
        noteFloatMenuFocusChange: function(seemsActive, focus, caller) {
//            log(""+new Date().getTime()+" note active "+caller+" "+seemsActive);
            var delayCheckFloat = true;
            var focusRowId = null;
            var focusElement = null;
            if (focus) {
                focusElement = focus.target ? focus.target : focus;
                if (seemsActive) {
                    this.lastFloatFocusInTextForEventUnmangling = $(focusElement).text();
                    focusRowId = focus.target ? $(focus.target).closest('tr').attr('id') : $(focus).closest('tr').attr('id');
                    if (this.floatMenuActive && focusRowId==this.lastFloatMenuRowId) {
                        // lastFloatMenuRowId has not changed, when moving within a floatgroup
                        // (but we still get mouseout events when the submenu changes)
//                        log("redundant mousein from "+ focusRowId );
                        return;
                    }
                } else {
                    // on mouseout, skip events which are bogus
                    // first, if the toElement is in the same floatGroup
                    focusRowId = focus.toElement ? $(focus.toElement).closest('tr').attr('id') : null;
                    if (focusRowId==this.lastFloatMenuRowId) {
                        // lastFloatMenuRowId has not changed, when moving within a floatgroup
                        // (but we still get mouseout events when the submenu changes)
//                        log("skipping, internal mouseout from "+ focusRowId );
                        return;
                    }
                    // check (a) it is the 'out' event corresponding to the most recent 'in'
                    // (because there is a race where it can say  in1, in2, out1 rather than in1, out2, in2
                    if ($(focusElement).text() != this.lastFloatFocusInTextForEventUnmangling) {
//                        log("skipping, not most recent mouseout from "+ focusRowId );
                        return;
                    }
                    if (focus.toElement) {
                        if ($(focus.toElement).hasClass('global-zeroclipboard-container')) {
//                            log("skipping out, as we are moving to clipboard container");
                            return;
                        }
                        if (focus.toElement.name && focus.toElement.name=="global-zeroclipboard-flash-bridge") {
//                            log("skipping out, as we are moving to clipboard movie");
                            return;                            
                        }
                    }
                } 
            }           
//            log( "moving to "+focusRowId );
            if (seemsActive && focusRowId) {
//                log("setting lastFloat when "+this.floatMenuActive + ", from "+this.lastFloatMenuRowId );
                if (this.lastFloatMenuRowId != focusRowId) {
                    if (this.lastFloatMenuRowId) {
                        // the floating menu has changed, hide the old
//                        log("hiding old menu on float-focus change");
                        this.closeFloatMenuNow();
                    }
                }
                // now show the new, if possible (might happen multiple times, but no matter
                if (focusElement) {
//                    log("ensuring row "+focusRowId+" is showing on change");
                    this.showFloatLeftOf($(focusElement).closest('tr').find('.hasFloatLeft'));
                    this.lastFloatMenuRowId = focusRowId;
                } else {
                    this.lastFloatMenuRowId = null;
                }
            }
            this.floatMenuActive = seemsActive;
            if (!seemsActive) {
                this.scheduleCheckFloatMenuNeedsHiding(delayCheckFloat);
            }
        },
        scheduleCheckFloatMenuNeedsHiding: function(delayCheckFloat) {
            if (delayCheckFloat) {
                this.checkTime = new Date().getTime()+299;
                setTimeout(this.checkFloatMenuNeedsHiding, 300);
            } else {
                this.checkTime = new Date().getTime()-1;
                this.checkFloatMenuNeedsHiding();
            }
        },
        closeFloatMenuNow: function() {
//            log("closing float menu due do direct call (eg click)");
            this.checkTime = new Date().getTime()-1;
            this.floatMenuActive = false;
            this.checkFloatMenuNeedsHiding();
        },
        checkFloatMenuNeedsHiding: function() {
//            log(""+new Date().getTime()+" checking float menu - "+this.floatMenuActive);
            if (new Date().getTime() <= this.checkTime) {
//                log("aborting check as another one scheduled");
                return;
            }
            
            // we use a flag to determine whether to hide the float menu
            // because the embedded zero-clipboard flash objects cause floatGroup 
            // to get a mouseout event when the "Copy" menu item is hovered
            if (!this.floatMenuActive) {
//                log("HIDING FLOAT MENU")
                $('.floatLeft').hide(); 
                $('.floatDown').hide();
                $('.zeroclipboard-is-hover').removeClass('zeroclipboard-is-hover');
                lastFloatMenuRowId = null;
            } else {
//                log("we're still in")
            }
        },
        valueOpen: function(event) {
            window.open($(event.target).attr('open-target'),'_blank');
        },

        render: function() {
            return this;
        },
        checkFloatMenuUpToDate: function($row, actionValue, actionSelector, actionAttribute) {
            if (typeof actionValue === 'undefined' || actionValue==null || actionValue=="") {
                if ($row.find(actionSelector).length==0) return true;
            } else {
                if (actionAttribute) {
                    if ($row.find(actionSelector).attr(actionAttribute)==actionValue) return true;
                } else {
                    if ($row.find(actionSelector).length>0) return true;
                }
            }
            return false;
        },
        
        /**
         * Returns the actions loaded to view.configMetadata[name].actions
         * for the given name, or an empty object.
         */
        getConfigActions: function(configName) {
            var allMetadata = this.configMetadata || {};
            var metadata = allMetadata[configName] || {};
            return metadata.actions || {};
        },

        toggleFilterEmpty: function() {
            ViewUtils.toggleFilterEmpty(this.$('#config-table'), 2);
            return this;
        },

        toggleAutoRefresh: function() {
            ViewUtils.toggleAutoRefresh(this);
            return this;
        },

        enableAutoRefresh: function(isEnabled) {
            this.refreshActive = isEnabled;
            return this;
        },
        
        /**
         * Loads current values for all config on an entity and updates config table.
         */
        isRefreshActive: function() { return this.refreshActive; },
        updateConfigNow:function () {
            var that = this;
            ViewUtils.get(that, that.model.getConfigUpdateUrl(), that.updateWithData,
                    { enablement: that.isRefreshActive });
        },
        updateConfigPeriodically:function () {
            var that = this;
            ViewUtils.getRepeatedlyWithDelay(that, that.model.getConfigUpdateUrl(), function(data) { that.updateWithData(data); },
                    { enablement: that.isRefreshActive });
        },
        updateWithData: function (data) {
            var that = this;
            $table = that.$('#config-table');
            var options = {};
            
            if (that.fullRedraw) {
                options.refreshAllRows = true;
                that.fullRedraw = false;
            }
            ViewUtils.updateMyDataTable($table, data, function(value, name) {
                var metadata = that.configMetadata[name];
                if (metadata==null) {                        
                    // kick off reload metadata when this happens (new config for which no metadata known)
                    // but only if we haven't loaded metadata for a while
                    metadata = { 'name':name };
                    that.configMetadata[name] = metadata; 
                    that.loadConfigMetadataIfStale(name, 10000);
                } 
                return [name, metadata, value];
            }, options);
            
            that.updateFloatMenus();
        },

        loadConfigMetadata: function() {
            var url = this.model.getLinkByName('config'),
                that = this;
            that.lastConfigMetadataLoadTime = new Date().getTime();
            $.get(url, function (data) {
                _.each(data, function(config) {
                    var actions = {};
                    _.each(config.links, function(v, k) {
                        if (k.slice(0, 7) == "action:") {
                            actions[k.slice(7)] = v;
                        }
                    });
                    that.configMetadata[config.name] = {
                        name: config.name,
                        description: config.description,
                        actions: actions,
                        type: config.type
                    };
                });
                that.fullRedraw = true;
                that.updateConfigNow();
                that.table.find('*[rel="tooltip"]').tooltip();
            });
            return this;
        },
        
        loadConfigMetadataIfStale: function(configName, recency) {
            var that = this;
            if (!that.lastConfigMetadataLoadTime || that.lastConfigMetadataLoadTime + recency < new Date().getTime()) {
//                log("reloading metadata because new config "+configName+" identified")
                that.loadConfigMetadata();
            }
        }
    });
    return EntityConfigView;
});
