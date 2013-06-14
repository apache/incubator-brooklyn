define([
        "underscore", "jquery", "backbone"
], function (_, $, Backbone) {

    var ViewUtils = {
        myDataTable:function($table, extra) {
            var settings = {
                "bDestroy": true,
                "iDisplayLength": 25,
                "sPaginationType": "full_numbers",
                "sDom": "fp<'brook-db-top-toolbar'>tilp<'brook-db-bot-toolbar'>",
                "oLanguage": {
                    "sSearch": "",
                    "sInfo": "Showing _START_ - _END_ of _TOTAL_ ",
                    "sInfoEmpty": "<i>No data</i> ",
                    "sEmptyTable": "<i>No matching records available</i>",
                    "sZeroRecords": "<i>No matching records found</i>",
                    "oPaginate": {
                        "sFirst": "&lt;&lt;",
                        "sPrevious": "&lt;",
                        "sNext": "&gt;",
                        "sLast": "&gt;&gt;"
                    },
                    "sInfoFiltered": "(of _MAX_)",
                    "sLengthMenu": '( <select>' +
                                        '<option value="10">10</option>' +
                                        '<option value="25">25</option>' +
                                        '<option value="50">50</option>' +
                                        '<option value="-1">all</option>' +
                                    '</select> / page )'
                }
            };
            for (var prop in extra) {
                settings[prop] = extra[prop];
            }
            var result = $table.dataTable(settings);
            return result;
        },
        myDataTableToolbarAddHtml: function($table,html) {
            $('.brook-db-bot-toolbar', $table.parent().parent()).append(html)
            $('.brook-db-top-toolbar', $table.parent().parent()).append(html)
        },
        addRefreshButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="refresh table-toolbar-icon icon-refresh handy smallpadside" rel="tooltip" title="Reload content immediately"></i>');
        },
        addFilterEmptyButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="filterEmpty table-toolbar-icon icon-eye-open handy bottom smallpadside" rel="tooltip" title="Show/hide empty records"></i>');
        },
        addAutoRefreshButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="toggleAutoRefresh table-toolbar-icon icon-pause handy smallpadside" rel="tooltip" title="Toggle auto-refresh"></i>');
        },
        /* fnConvertData takes the entries in collection (value, optionalKeyOrIndex) and returns a list
         * whose first element is the ID (hidden first column of table)
         * and other elements are the other columns in the table;
         * alternatively it can return null if the entry should be excluded
         */ 
        updateMyDataTable: function(table, collection, fnConvertData) {
            if (table==null) return;
            var oldDisplayDataList = table.dataTable().fnGetData();
            var oldDisplayIndexMap = {}
            var oldDisplayData = {}
            for (var idx in oldDisplayDataList) {
                var data = oldDisplayDataList[idx]
                oldDisplayIndexMap[data[0]] = idx
                oldDisplayData[data[0]] = data
            }
            var newDisplayData = {}
            var updateDisplayData = []
            ViewUtils.each(collection, function(data,index) { 
                var newRow = fnConvertData(data, index)
                if (newRow!=null) {
                    var id = newRow[0]

                    var displayIndex = oldDisplayIndexMap[id];
                    if (displayIndex!=null) {
                        updateDisplayData[displayIndex] = newRow
                        delete oldDisplayIndexMap[id]
                    } else {
                        newDisplayData[id] = newRow
                    }
                }
            })
            // first update (so indices don't change)
            for (var prop in updateDisplayData) {
                var rowProps = updateDisplayData[prop]
                var oldProps = oldDisplayData[rowProps[0]]
                for (idx in rowProps) {
                    var v = rowProps[idx]
                    if (!_.isEqual(v,oldProps[idx])) {
                        // update individual columns as values change
//                        log("updating "+v+" in "+prop+"["+idx+"]")
                        table.fnUpdate( v, Number(prop), idx, false, false )
                    } else {
//                        log("NO CHANGE")
                    }
                }
            }
            // then delete old ones
            for (var prop in oldDisplayIndexMap) {
                var index = oldDisplayIndexMap[prop]
//                log("deleting "+index)
                table.fnDeleteRow( Number(index), null, false )
            }
            // and now add new ones
            for (var prop in newDisplayData) {
//                log("adding "+newDisplayData[prop])
                table.fnAddData( newDisplayData[prop] )
            }
            table.fnAdjustColumnSizing();
            table.fnStandingRedraw();
        },
        toggleFilterEmpty: function($table, column) {
            var hideEmpties = $('.filterEmpty', $table.parent().parent()).toggleClass('icon-eye-open icon-eye-close').hasClass('icon-eye-close');
            if (hideEmpties) {
                $table.dataTable().fnFilter('.+', column, true);
            } else {
                $table.dataTable().fnFilter('.*', column, true);
            }
        },
        toggleAutoRefresh: function(pane) {
            var isEnabled = $('.toggleAutoRefresh', pane.$el).toggleClass('icon-pause icon-play').hasClass('icon-pause');
            pane.enableAutoRefresh(isEnabled);
        },
        attachToggler: function($scope) {
            var $togglers;
            if ($scope === undefined) {
                $togglers = $(".toggler-header");
            } else {
                $togglers = $(".toggler-header", $scope);
            }
            $togglers.click(this.onTogglerClick);
        },
        onTogglerClick: function(event) {
            var root = $(event.currentTarget).closest(".toggler-header");
            root.toggleClass("user-hidden");
            $(".toggler-icon", root).toggleClass("icon-chevron-left").toggleClass("icon-chevron-down");
            var next = root.next();
            if (root.hasClass("user-hidden")) {
                next.slideUp('fast');
            } else {
                next.slideDown('fast');
            }
        },
        updateTextareaWithData: function($div, data, alwaysShow, minPx, maxPx) {
            var $ta = $("textarea", $div);
            var show = alwaysShow;
            if (data !== undefined) {
                $ta.val(data);
                show = true;
            } else {
                $ta.val("");
            }
            if (show) {
                $div.show(100);
                $ta.css("height", minPx);
                // scrollHeight prop works sometimes (e.g. groovy page) but not others (e.g. summary)
                var height = $ta.prop("scrollHeight");
                height = Math.min(height, maxPx);
                height = Math.max(height, minPx);
                $ta.css("height", height);
            } else {
                $div.hide();
            }
        },
        each: function(collection, fn) {
            if (_.isFunction(collection.each)) {
                // some objects (such as backbone collections) are not iterable
                // (either by "for x in" or "_.each") so call the "each" method explicitly on them 
                return collection.each(fn)
            } else {
                // try underscore
                return _.each(collection, fn);
            }
        }
    };
    return ViewUtils;
});
