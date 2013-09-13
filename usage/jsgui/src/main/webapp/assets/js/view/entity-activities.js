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
        activityDetailsPanel:null,
        events:{
            "click #activities-root .activity-table tr":"rowClick",
            'click #activities-root .refresh':'refreshNow',
            'click #activities-root .toggleAutoRefresh':'toggleAutoRefresh',
            'click #activities-root .showDrillDown':'showDrillDown',
            'click #activities-root .toggleFullDetail':'toggleFullDetail'
        },
        initialize:function () {
            this.$el.html(this.template({ }));
            this.$('#activities-root').html(_.template(ActivityTableHtml))
            $.ajaxSetup({ async:true });
            var that = this,
                $table = that.$('#activities-root .activity-table');
            that.collection.url = that.model.getLinkByName("activities");
            that.table = ViewUtils.myDataTable($table, {
                "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                    $(nRow).attr('id', aData[0])
                    $(nRow).addClass('activity-row')
                },
                "aoColumnDefs": [
                                 {
                                     "mRender": function ( data, type, row ) {
                                         return Util.prep(data)
                                     },
                                     "aTargets": [ 1, 2, 3 ]
                                 },
                                 { "bVisible": false,  "aTargets": [ 0 ] }
                             ]            
            });
            
            // TODO domain-specific filters
            ViewUtils.addAutoRefreshButton(that.table);
            ViewUtils.addRefreshButton(that.table);
            
            ViewUtils.fadeToIndicateInitialLoad($table);
            that.collection.on("reset", that.renderOnLoad, that);
            that.callPeriodically("entity-activities", function () {
                if (that.refreshActive)
                    that.collection.fetch({reset: true});
            }, 3000);
            that.collection.fetch({reset: true});
        },
        refreshNow: function() {
            this.collection.fetch({reset: true});
        },
        render:function () {
            this.updateActivitiesNow(this);
            return this;
        },
        beforeClose:function () {
            this.collection.off("reset", this.renderOnLoad);
        },
        renderOnLoad: function() {
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
                             moment(task.get("submitTimeUtc")).calendar(),
                             task.get("currentStatus")
                    ]; 
                });
                this.showDetailRow(true);
            }
            return this;
        },
        rowClick:function(evt) {
            var that = this;
            var row = $(evt.currentTarget).closest("tr");
            var table = $(evt.currentTarget).closest(".activity-table");
            var id = row.attr("id");
            
            if (id==null)
                // is the details row, ignore click here
                return;

            this.showDrillDownTask(id);
            return;
            // below this line in this function (and much of the other functions here)
            // would replace the above to show an in-line short-form view of the task;
            // drill-down is more useful however, i think

            $(table).find("tr").removeClass("selected");
            
            if (this.selectedRow!=null) {
                var r = this.selectedRow;
                // slide it up, then close once it is hidden (else it vanishes suddenly)
                // the slide effect isn't just cool, it helps keep rows in a visually consistent place
                // (so that it doesn't just jump when you click, if a row above it was previously selected)
                $('tr[#'+id+'] .opened-row-details').slideUp(300, function() { 
                    that.table.fnClose(r);
                })
            }
            
            if (this.selectedId == id) {
                // deselected
                this.selectedRow = null;
                this.selectedId = null;
                this.hideFullActivity(id);
            } else {
                row.addClass("selected");
                this.selectedRow = row[0];
                this.selectedId = id;
                this.table.fnOpen(row[0], '', 'row-expansion'); 
                this.showDetailRow(false);
            }
        },
        showDetailRow: function(updateOnly) {
//            // auto-drill-down -- useful for testing
//            if (this.selectedId==null) {
//                log("auto-selecting")
//                this.selectedId = this.collection.models[0].get('id')
//                this.showDrillDownTask(this.selectedId)
//            }
            
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
                $('tr#'+id).next().find('.task-description').html(Util.prep(task.attributes.description))
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
        },
        showDrillDown: function(event) {
            this.showDrillDownTask($(event.currentTarget).closest("td.row-expansion").attr("id"));
        },
        showDrillDownTask: function(taskId) {    
            this.activityDetailsPanel = new ActivityDetailsView({
                task: this.collection.get(taskId),
                collection: this.collection,
                breadcrumbs: ''
            })
            var $t = this.$('#activities-root')
            $t2 = $t.after('<div>').next()
            $t2.addClass('slide-panel')
            
            $t2.html(this.activityDetailsPanel.render().el)

            $t.animate({
                    left: -600
                }, 300, function() { 
                    $t.hide() 
                });

            $t2.show().css({
                    left: 600
                    , top: 0
                }).animate({
                    left: 0
                }, 300);
        }
    });

    return ActivitiesView;
});
