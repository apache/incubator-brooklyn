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
 * Displays details on an activity/task
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "view/viewutils", "formatJson", "moment",
    "model/task-summary",
    "text!tpl/apps/activity-details.html", "text!tpl/apps/activity-table.html", 

    "bootstrap", "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Util, ViewUtils, FormatJSON, moment,
    TaskSummary,
    ActivityDetailsHtml, ActivityTableHtml) {

    var activityTableTemplate = _.template(ActivityTableHtml),
        activityDetailsTemplate = _.template(ActivityDetailsHtml);

    function makeActivityTable($el) {
        $el.html(_.template(ActivityTableHtml));
        var $subTable = $('.activity-table', $el);
        $subTable.attr('width', 569-6-6 /* subtract padding */)

        return ViewUtils.myDataTable($subTable, {
            "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                $(nRow).attr('id', aData[0])
                $(nRow).addClass('activity-row')
            },
            "aoColumnDefs": [ {
                    "mRender": function ( data, type, row ) { return Util.escape(data) },
                    "aTargets": [ 1, 2, 3 ]
                 }, {
                    "bVisible": false,
                    "aTargets": [ 0 ]
                 } ],
            "aaSorting":[]  // default not sorted (server-side order)
        });
    }

    var ActivityDetailsView = Backbone.View.extend({
        template: activityDetailsTemplate,
        taskLink: '',
        task: null,
        /* children of this task; see HasTaskChildren for difference between this and sub(mitted)Tasks */
        childrenTable: null,
        /* tasks in the current execution context (this.collections) whose submittedByTask
         * is the task we are drilled down on. this defaults to the passed in collection, 
         * which will be the last-viewed entity's exec-context; when children cross exec-context
         * boundaries we have to rewire to point to the current entity's exec-context / tasks */
        subtasksTable: null,
        children: null,
        breadcrumbs: [],
        firstLoad: true,
        events:{
            "click #activities-children-table .activity-table tr":"childrenRowClick",
            "click #activities-submitted-table .activity-table tr":"submittedRowClick",
            'click .showDrillDownSubmittedByAnchor':'showDrillDownSubmittedByAnchor',
            'click .showDrillDownBlockerOfAnchor':'showDrillDownBlockerOfAnchor',
            'click .backDrillDown':'backDrillDown'
        },
        // requires taskLink or task; breadcrumbs is optional
        initialize:function () {
            var that = this;
            this.taskLink = this.options.taskLink;
            this.taskId = this.options.taskId;
            if (this.options.task)
                this.task = this.options.task;
            else if (this.options.tabView)
                this.task = this.options.tabView.collection.get(this.taskId);
            if (!this.taskLink && this.task) this.taskLink = this.task.get('links').self;
            if (!this.taskLink && this.taskId) this.taskLink = "v1/activities/"+this.taskId;;
            
            this.tabView = this.options.tabView || null;
            
            if (this.options.breadcrumbs) this.breadcrumbs = this.options.breadcrumbs;

            this.$el.html(this.template({ taskLink: this.taskLink, taskId: this.taskId, task: this.task, breadcrumbs: this.breadcrumbs }));
            this.$el.addClass('activity-detail-panel');

            this.childrenTable = makeActivityTable(this.$('#activities-children-table'));
            this.subtasksTable = makeActivityTable(this.$('#activities-submitted-table'));

            ViewUtils.attachToggler(this.$el)
        
            if (this.task) {
                this.renderTask()
                this.setUpPolling()
            } else {      
                ViewUtils.fadeToIndicateInitialLoad(this.$el);
                this.$el.css('cursor', 'wait')
                $.get(this.taskLink, function(data) {
                    ViewUtils.cancelFadeOnceLoaded(that.$el);
                    that.task = new TaskSummary.Model(data)
                    that.renderTask()
                    that.setUpPolling();
                }).fail(function() { log("unable to load "+that.taskLink) })
            }

            // initial subtasks may be available from parent, so try to render those
            // (reliable polling for subtasks, and for children, is set up in setUpPolling ) 
            this.renderSubtasks()
        },
        
        refreshNow: function(initial) {
            var that = this
            $.get(this.taskLink, function(data) {
                that.task = new TaskSummary.Model(data)
                that.renderTask()
                if (initial) that.setUpPolling();
            })
        },
        renderTask: function() {
            // update task fields
            var that = this, firstLoad = this.firstLoad;
            this.firstLoad = false;
            
            if (firstLoad  && this.task) {
//                log("rendering "+firstLoad+" "+this.task.get('isError')+" "+this.task.id);
                if (this.task.get('isError')) {
                    // on first load, expand the details if there is a problem
                    var $details = this.$(".toggler-region.task-detail .toggler-header");
                    ViewUtils.showTogglerClickElement($details);
                }
            }
            
            this.updateFields('displayName', 'entityDisplayName', 'id', 'description', 'currentStatus', 'blockingDetails');
            this.updateFieldWith('blockingTask',
                function(v) { 
                    return "<a class='showDrillDownBlockerOfAnchor handy' link='"+_.escape(v.link)+"' id='"+v.metadata.id+"'>"+
                        that.displayTextForLinkedTask(v)+"</a>" })
            this.updateFieldWith('result',
                function(v) {
                    // use display string (JSON.stringify(_.escape(v)) because otherwise list of [null,null] is just ","  
                    var vs = Util.toDisplayString(v);
                    if (vs.trim().length==0) {
                        return " (empty result)";
                    } else if (vs.length<20 &&  !/\r|\n/.exec(v)) {
                        return " with result: <span class='result-literal'>"+vs+"</span>";
                    } else {
                        return "<div class='result-literal'>"+vs.replace(/\n+/g,"<br>")+"</div>"
                    }
                 })
            this.updateFieldWith('tags', function(tags) {
                var tagBody = "";
                for (var tag in tags)
                    tagBody += "<div class='activity-tag-giftlabel'>"+_.escape(tags[tag])+"</div>";
                return tagBody;
            })
            
            var submitTimeUtc = this.updateFieldWith('submitTimeUtc',
                function(v) { return v <= 0 ? "-" : moment(v).format('D MMM YYYY H:mm:ss.SSS')+" &nbsp; <i>"+moment(v).fromNow()+"</i>" })
            var startTimeUtc = this.updateFieldWith('startTimeUtc',
                function(v) { return v <= 0 ? "-" : moment(v).format('D MMM YYYY H:mm:ss.SSS')+" &nbsp; <i>"+moment(v).fromNow()+"</i>" })
            this.updateFieldWith('endTimeUtc',
                function(v) { return v <= 0 ? "-" : moment(v).format('D MMM YYYY H:mm:ss.SSS')+" &nbsp; <i>"+moment(v).from(startTimeUtc, true)+" later</i>" })

            ViewUtils.updateTextareaWithData(this.$(".task-json .for-textarea"), 
                FormatJSON(this.task.toJSON()), false, false, 150, 400)

            ViewUtils.updateTextareaWithData(this.$(".task-detail .for-textarea"), 
                this.task.get('detailedStatus'), false, false, 30, 250)

            this.updateFieldWith('streams',
                function(v) {
                    var result = "";
                    for (var si in v) {
                        var sv = v[si];
                        result += "<div class='activity-stream-div'>"+
                                  "<span class='activity-label'>"+
                                    _.escape(si)+
                                  "</span><span>"+
                                      "<a href='"+sv.link+"'>download</a>"+
                                      (sv.metadata["sizeText"] ? " ("+_.escape(sv.metadata["sizeText"])+")" : "")+
                                  "</span></div>";
                    }
                    return result; 
                })

            this.updateFieldWith('submittedByTask',
                function(v) { return "<a class='showDrillDownSubmittedByAnchor handy' link='"+_.escape(v.link)+"' id='"+v.metadata.id+"'>"+
                    that.displayTextForLinkedTask(v)+"</a>" })

            if (this.task.get("children").length==0)
                this.$('.toggler-region.tasks-children').hide();
        },
        setUpPolling: function() {
            var that = this

            // on first load, clear any funny cursor
            this.$el.css('cursor', 'auto')

            this.task.url = this.taskLink;
            this.task.on("all", this.renderTask, this)
            
            ViewUtils.get(this, this.taskLink, function(data) {
                // if we can get the data, then start fetching certain things repeatedly
                // (would be good to skip the immediate "doitnow" below but not a big deal)
                ViewUtils.fetchRepeatedlyWithDelay(that, that.task, { doitnow: true });
                
                // and set up to load children (now that the task is guaranteed to be loaded)
                that.children = new TaskSummary.Collection()
                that.children.url = that.task.get("links").children
                that.children.on("reset", that.renderChildren, that)
                ViewUtils.fetchRepeatedlyWithDelay(that, that.children, { 
                    fetchOptions: { reset: true }, doitnow: true, fadeTarget: that.$('.tasks-children') });
            }).fail( function() { that.$('.toggler-region.tasks-children').hide() } );


            $.get(this.task.get("links").entity, function(entity) {
                if (that.collection==null || entity.links.activities != that.collection.url) {
                    // need to rewire collection to point to the right ExecutionContext
                    that.collection = new TaskSummary.Collection()
                    that.collection.url = entity.links.activities
                    that.collection.on("reset", that.renderSubtasks, that)
                    ViewUtils.fetchRepeatedlyWithDelay(that, that.collection, { 
                        fetchOptions: { reset: true }, doitnow: true, fadeTarget: that.$('.tasks-submitted') });
                } else {
                    that.collection.on("reset", that.renderSubtasks, that)
                    that.collection.fetch({reset: true});
                }
            });
        },
        
        renderChildren: function() {
            var that = this
            var children = this.children
            ViewUtils.updateMyDataTable(this.childrenTable, children, function(task, index) {
                return [ task.get("id"),
                         (task.get("entityId") && task.get("entityId")!=that.task.get("entityId") ? task.get("entityDisplayName") + ": " : "") + 
                         task.get("displayName"),
                         task.get("submitTimeUtc") <= 0 ? "-" : moment(task.get("submitTimeUtc")).calendar(),
                         task.get("currentStatus")
                    ]; 
                });
            if (children && children.length>0) {
                this.$('.toggler-region.tasks-children').show();
            } else {
                this.$('.toggler-region.tasks-children').hide();
            }
        },
        renderSubtasks: function() {
            var that = this
            var taskId = this.taskId || (this.task ? this.task.id : null);
            if (!this.collection) {
                this.$('.toggler-region.tasks-submitted').hide();
                return;
            }
            if (!taskId) {
                // task not available yet; just wait for it to be loaded
                // (and in worst case, if it can't be loaded, this panel stays faded)
                return;
            } 
            
            // find tasks submitted by this one which aren't included as children
            // this uses collections -- which is everything in the current execution context
            var subtasks = []
            for (var taskI in this.collection.models) {
                var task = this.collection.models[taskI]
                var submittedBy = task.get("submittedByTask")
                if (submittedBy!=null && submittedBy.metadata!=null && submittedBy.metadata["id"] == taskId &&
                        (!this.children || this.children.get(task.id)==null)) {
                    subtasks.push(task)
                }
            }
            ViewUtils.updateMyDataTable(this.subtasksTable, subtasks, function(task, index) {
                return [ task.get("id"),
                         (task.get("entityId") && (!that.task || task.get("entityId")!=that.task.get("entityId")) ? task.get("entityDisplayName") + ": " : "") + 
                         task.get("displayName"),
                         task.get("submitTimeUtc") <= 0 ? "-" : moment(task.get("submitTimeUtc")).calendar(),
                         task.get("currentStatus")
                    ];
                });
            if (subtasks && subtasks.length>0) {
                this.$('.toggler-region.tasks-submitted').show();
            } else {
                this.$('.toggler-region.tasks-submitted').hide();
            }
        },
        
        displayTextForLinkedTask: function(v) {
            return v.metadata.taskName ? 
                    (v.metadata.entityDisplayName ? _.escape(v.metadata.entityDisplayName)+" <b>"+_.escape(v.metadata.taskName)+"</b>" : 
                        _.escape(v.metadata.taskName)) :
                    v.metadata.taskId ? _.escape(v.metadata.taskId) : 
                    _.escape(v.link)
        },
        updateField: function(field) {
            return this.updateFieldWith(field, _.escape)
        },
        updateFields: function() {
            _.map(arguments, this.updateField, this);
        },
        updateFieldWith: function(field, f) {
            var v = this.task.get(field)
            if (v !== undefined && v != null && 
                    (typeof v !== "object" || _.size(v) > 0)) {
                this.$('.updateField-'+field, this.$el).html( f(v) );
                this.$('.ifField-'+field, this.$el).show();
            } else {
                // blank if there is no value
                this.$('.updateField-'+field).empty();
                this.$('.ifField-'+field).hide();
            }
            return v
        },
        childrenRowClick:function(evt) {
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            this.showDrillDownTask("subtask of", this.children.get(id).get("links").self, id, this.children.get(id))
        },
        submittedRowClick:function(evt) {
            var row = $(evt.currentTarget).closest("tr");
            var id = row.attr("id");
            // submitted tasks are guaranteed to be in the collection, so this is safe
            this.showDrillDownTask("subtask of", this.collection.get(id).get('links').self, id)
        },
        
        showDrillDownSubmittedByAnchor: function(from) {
            var $a = $(from.target).closest('a');
            this.showDrillDownTask("submitter of", $a.attr("link"), $a.attr("id"))
        },
        showDrillDownBlockerOfAnchor: function(from) {
            var $a = $(from.target).closest('a');
            this.showDrillDownTask("blocker of", $a.attr("link"), $a.attr("id"))
        },
        showDrillDownTask: function(relation, newTaskLink, newTaskId, newTask) {
//            log("activities deeper drill down - "+newTaskId +" / "+newTaskLink)
            var that = this;
            
            var newBreadcrumbs = [ relation + ' ' +
                this.task.get('entityDisplayName') + ' ' +
                this.task.get('displayName') ].concat(this.breadcrumbs)
                
            var activityDetailsPanel = new ActivityDetailsView({
                taskLink: newTaskLink,
                taskId: newTaskId,
                task: newTask,
                tabView: that.tabView,
                collection: this.collection,
                breadcrumbs: newBreadcrumbs
            });
            activityDetailsPanel.addToView(this.$el);
        },
        addToView: function(parent) {
            if (this.parent) {
                log("WARN: adding details to view when already added")
                this.parent = parent;
            }
            
            if (Backbone.history && (!this.tabView || !this.tabView.openingQueuedTasks)) {
                Backbone.history.navigate(Backbone.history.fragment+"/"+"subtask"+"/"+this.taskId);
            }

            var $t = parent.closest('.slide-panel');
            var $t2 = $t.after('<div>').next();
            $t2.addClass('slide-panel');

            // load the drill-down page
            $t2.html(this.render().el)

            var speed = (!this.tabView || !this.tabView.openingQueuedTasks) ? 300 : 0;
            $t.animate({
                    left: -600
                }, speed, function() { 
                    $t.hide() 
                });

            $t2.show().css({
                    left: 600
                    , top: 0
                }).animate({
                    left: 0
                }, speed);
        },
        backDrillDown: function(event) {
//            log("activities drill back from "+this.taskLink)
            var that = this
            var $t2 = this.$el.closest('.slide-panel')
            var $t = $t2.prev()

            if (Backbone.history) {
                var fragment = Backbone.history.fragment
                var thisLoc = fragment.indexOf("/subtask/"+this.taskId);
                if (thisLoc>=0)
                    Backbone.history.navigate( fragment.substring(0, thisLoc) );
            }

            $t2.animate({
                    left: 569 //prevTable.width()
                }, 300, function() {
                    that.$el.empty()
                    $t2.remove()
                    that.remove()
                });

            $t.show().css({
                    left: -600 //-($t2.width())
                }).animate({
                    left: 0
                }, 300);
        }
    });

    return ActivityDetailsView;
});
