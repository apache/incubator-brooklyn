/**
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "view/viewutils",
    "text!tpl/apps/activities.html", "text!tpl/apps/activity-table.html", 
    "text!tpl/apps/activity-row-details.html", "text!tpl/apps/activity-row-details-main.html",
    "text!tpl/apps/activity-full-details.html", 
    "bootstrap", "formatJson", "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Util, ViewUtils, 
    ActivitiesHtml, ActivityTableHtml, ActivityRowDetailsHtml, ActivityRowDetailsMainHtml, ActivityFullDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        table:null,
        refreshActive:true,
        selectedId:null,
        selectedRow:null,
        events:{
            "click .activity-table tr":"rowClick",
            'click .refresh':'refreshNow',
            'click .toggleAutoRefresh':'toggleAutoRefresh',
            'click .showDrillDown':'showDrillDown',
            'click .backDrillDown':'backDrillDown',
            'click .toggleFullDetail':'toggleFullDetail'
        },
        initialize:function () {
            this.$el.html(this.template({ }));
            this.$('#activities-root').html(_.template(ActivityTableHtml))
            $.ajaxSetup({ async:false });
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
            
            that.collection.on("reset", that.render, that);
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
            this.collection.off("reset", this.render);
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
            if (that.table == null || this.collection.length==0) {
                // nothing to do
            } else {
                ViewUtils.updateMyDataTable(that.table, that.collection, function(task, index) {
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
            var that = this;
            var row = $(evt.currentTarget).closest("tr");
            var table = $(evt.currentTarget).closest(".activity-table");
            var id = row.attr("id");
            
            if (id==null)
                // is the details row, ignore click here
                return;

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
//                this.showDrillDown(null);
            } else {
                row.addClass("selected");
                this.selectedRow = row[0];
                this.selectedId = id;
                this.table.fnOpen(row[0], '', 'row-expansion'); 
                this.showDetailRow(false);
            }
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
        backDrillDown: function(parent) {
            var $t = this.$('#activities-root')
            var $t2 = this.$('#1234') 
            $t2.animate({
                    left: 569 //prevTable.width()
                }, 500, function() { 
                    $t2.remove() 
                });

            $t.show().css({
                    left: -600 //-($t2.width())
                }).animate({
                    left: 0
                }, 500);
        },
        showDrillDown: function(event) {
            var parentId = $(event.currentTarget).closest("td.row-expansion").attr("id");
            log("WIP drill down - "+parentId)
            log(this.collection)
            notImplementedYet;
            
            var $t = this.$('#activities-root')
            //   style="display: inline-block; overflow: hidden; white-space: nowrap;"
            $t2 = $t.after('<div>').next()
            $t2.attr('id', '1234')
            $t2.addClass('slide-panel')
            $t2.hide()
            $t2.append('<div class="subpanel-header-row backDrillDown">'+
                    '<i class="backDrillDown icon-chevron-left handy" rel="tooltip" title="Return to sibling tasks" style="margin-top: 4px;"></i>'+
                    '&nbsp; Sub-tasks of: \'sample 1234\''+
                    '</div>')
            $t2.append('<div class="table-scroll-wrapper"></div>')
            $t2.find('.table-scroll-wrapper').append(_.template(ActivityTableHtml))
            $t2t = $t2.find('table.activity-table')
            
            table2 = ViewUtils.myDataTable( $t2t, {
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
            table2.fnAddData( [ "XXX", "Sample sub-task", "MOCK", "(work in progress)" ])

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
            log("FULL for "+id)
            id = this.selectedId
            var $details = $("td.row-expansion#"+id+" .expansion-footer");
            var task = this.collection.get(id);
            var html = _.template(ActivityFullDetailsHtml, { task: task });
            $details.html(html);
            $details.slideDown(100);
        },
        hideFullActivity: function(id) {
            id = this.selectedId
            var $details = $("td.row-expansion#"+id+" .expansion-footer");
            $details.slideUp(100);
        }
    });

    return ActivitiesView;
});
