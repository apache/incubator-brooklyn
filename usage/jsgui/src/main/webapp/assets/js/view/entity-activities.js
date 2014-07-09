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
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "view/viewutils",
    "view/activity-details",
    "text!tpl/apps/activities.html", "text!tpl/apps/activity-table.html", 
    "text!tpl/apps/activity-row-details.html", "text!tpl/apps/activity-row-details-main.html",
    "text!tpl/apps/activity-full-details.html", 
    "bootstrap", "formatJson", "jquery-datatables", "datatables-extensions", "moment"
], function (_, $, Backbone, Util, ViewUtils, ActivityDetailsView, 
    ActivitiesHtml, ActivityTableHtml, ActivityRowDetailsHtml, ActivityRowDetailsMainHtml, ActivityFullDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        table:null,
        refreshActive:true,
        selectedId:null,
        selectedRow:null,
        events:{
            "click #activities-root .activity-table tr":"rowClick",
            'click #activities-root .refresh':'refreshNow',
            'click #activities-root .toggleAutoRefresh':'toggleAutoRefresh',
            'click #activities-root .showDrillDown':'showDrillDown',
            'click #activities-root .toggleFullDetail':'toggleFullDetail'
        },
        initialize:function () {
            _.bindAll(this)
            this.$el.html(this.template({ }));
            this.$('#activities-root').html(_.template(ActivityTableHtml))
            var that = this,
                $table = that.$('#activities-root .activity-table');
            that.collection.url = that.model.getLinkByName("activities");
            that.table = ViewUtils.myDataTable($table, {
                "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                    $(nRow).attr('id', aData[0])
                    $(nRow).addClass('activity-row')
                },
                "aaSorting": [[ 2, "desc" ]],
                "aoColumnDefs": [
                                 {
                                     "mRender": function ( data, type, row ) {
                                         return Util.escape(data)
                                     },
                                     "aTargets": [ 1, 3 ]
                                 },
                                 {
                                     "mRender": function ( data, type, row ) {
                                         if ( type === 'display' ) {
                                             data = moment(data).calendar();
                                         }
                                         return Util.escape(data)
                                     },
                                     "aTargets": [ 2 ]
                                 },
                                 { "bVisible": false,  "aTargets": [ 0 ] }
                             ]            
            });
            
            // TODO domain-specific filters
            ViewUtils.addAutoRefreshButton(that.table);
            ViewUtils.addRefreshButton(that.table);
            
            ViewUtils.fadeToIndicateInitialLoad($table);
            that.collection.on("reset", that.renderOnLoad, that);
            ViewUtils.fetchRepeatedlyWithDelay(this, this.collection, 
                    { fetchOptions: { reset: true }, doitnow: true, 
                    enablement: function() { return that.refreshActive }  });
        },
        refreshNow: function() {
            this.collection.fetch({reset: true});
        },
        render:function () {
            this.updateActivitiesNow();
            var details = this.options.tabView ? this.options.tabView.options.preselectTabDetails : null;
            if (details && details!=this.lastPreselectTabDetails) {
                this.lastPreselectTabDetails = details;
                // should be a path
                this.queuedTasksToOpen = details.split("/");
            }
            this.tryOpenQueuedTasks();
            return this;
        },
        tryOpenQueuedTasks: function() {
            if (!this.queuedTasksToOpen || this.tryingOpenQueuedTasks) return;
            this.openingQueuedTasks = true;
            var $lastActivityPanel = null;
            while (true) {
                var task = this.queuedTasksToOpen.shift();
                if (task == undefined) {
                    this.openingQueuedTasks = false;                    
                    return;
                }
                if (task == 'subtask') {
                    var subtask = this.queuedTasksToOpen.shift();
                    $lastActivityPanel = this.showDrillDownTask(subtask, $lastActivityPanel);
                } else {
                    log("unknown queued task for activities panel: "+task)
                    // skip it, just continue
                }
            }
        },
        beforeClose:function () {
            this.collection.off("reset", this.renderOnLoad);
        },
        renderOnLoad: function() {
            this.loaded = true;
            this.render();
            ViewUtils.cancelFadeOnceLoaded(this.table);
        },
        toggleAutoRefresh:function () {
            ViewUtils.toggleAutoRefresh(this);
        },
        enableAutoRefresh: function(isEnabled) {
            this.refreshActive = isEnabled
        },
        refreshNow: function() {
            this.collection.fetch();
            this.table.fnAdjustColumnSizing();
        },
        updateActivitiesNow: function() {
            var that = this;
            if (this.table == null || this.collection.length==0 || this.viewIsClosed) {
                // nothing to do
            } else {
                var topLevelTasks = []
                for (taskI in this.collection.models) {
                    var task = this.collection.models[taskI]
                    var submitter = task.get("submittedByTask")
                    if ((submitter==null) ||
                        (submitter!=null && this.collection.get(submitter.metadata.id)==null)
                    ) {                        
                        topLevelTasks.push(task)
                    }
                }
                ViewUtils.updateMyDataTable(that.table, topLevelTasks, function(task, index) {
                    return [ task.get("id"),
                             task.get("displayName"),
                             task.get("submitTimeUtc"),
                             task.get("currentStatus")
                    ]; 
                });
                this.showDetailRow(true);
            }
            return this;
        },
        rowClick:function(evt) {
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            if (id==null)
                // is the details row, ignore click here
                return;
            this.showDrillDownTask(id);
            return;
        },
        showDrillDown: function(event) {
            this.showDrillDownTask($(event.currentTarget).closest("td.row-expansion").attr("id"));
        },
        showDrillDownTask: function(taskId, optionalParent) {  
//            log("showing initial drill down "+taskId)
            var that = this;
            
            var activityDetailsPanel = new ActivityDetailsView({
                taskId: taskId,
                tabView: that,
                collection: this.collection,
                breadcrumbs: ''
            })
            activityDetailsPanel.addToView(optionalParent || this.$(".activity-table"));
            return activityDetailsPanel.$el;
        },
        
        showDetailRow: function(updateOnly) {
            var id = this.selectedId,
                that = this;
            if (id==null) return;
            var task = this.collection.get(id);
            if (task==null) return;
            if (!updateOnly) {
                var html = _.template(ActivityRowDetailsHtml, { 
                    task: task==null ? null : task.attributes,
                    link: that.model.getLinkByName("activities")+"/"+id,
                    updateOnly: updateOnly
                })
                $('tr#'+id).next().find('td.row-expansion').html(html)
                $('tr#'+id).next().find('td.row-expansion').attr('id', id)
            } else {
                // just update
                $('tr#'+id).next().find('.task-description').html(Util.escape(task.attributes.description))
            }
            
            var html = _.template(ActivityRowDetailsMainHtml, { 
                task: task==null ? null : task.attributes,
                link: that.model.getLinkByName("activities")+"/"+id,
                updateOnly: updateOnly 
            })
            $('tr#'+id).next().find('.expansion-main').html(html)
            
            
            if (!updateOnly) {
                $('tr#'+id).next().find('.row-expansion .opened-row-details').hide()
                $('tr#'+id).next().find('.row-expansion .opened-row-details').slideDown(300)
            }
        },
        toggleFullDetail: function(evt) {
            var i = $('.toggleFullDetail');
            var id = i.closest("td.row-expansion").attr('id')
            i.toggleClass('active')
            if (i.hasClass('active'))
                this.showFullActivity(id)
            else
                this.hideFullActivity(id)
        },
        showFullActivity: function(id) {
            id = this.selectedId
            var $details = $("td.row-expansion#"+id+" .expansion-footer");
            var task = this.collection.get(id);
            var html = _.template(ActivityFullDetailsHtml, { task: task });
            $details.html(html);
            $details.slideDown(100);
            _.defer(function() { ViewUtils.setHeightAutomatically($('textarea',$details), 30, 200) })
        },
        hideFullActivity: function(id) {
            id = this.selectedId
            var $details = $("td.row-expansion#"+id+" .expansion-footer");
            $details.slideUp(100);
        }
    });

    return ActivitiesView;
});
