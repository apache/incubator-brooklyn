define([
        "underscore", "jquery", "brooklyn"
], function (_, $, BrooklynConfig) {

    var ViewUtils = {
        myDataTable:function($table, extra) {
            $.fn.dataTableExt.sErrMode = 'throw';
            var settings = {
                "bDestroy": true,
                "iDisplayLength": 25,
                "bDeferRender": true,
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
            _.extend(settings, extra);
            
            ViewUtils.fadeToIndicateInitialLoad($table);
 
            return $table.dataTable(settings);
        },
        myDataTableToolbarAddHtml: function($table,html) {
            $('.brook-db-bot-toolbar', $table.parent().parent()).append(html)
            $('.brook-db-top-toolbar', $table.parent().parent()).append(html)
        },
        addRefreshButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="refresh table-toolbar-icon bootstrap-glyph icon-refresh handy smallpadside" rel="tooltip" title="Reload content immediately"></i>');
        },
        addFilterEmptyButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="filterEmpty table-toolbar-icon bootstrap-glyph icon-eye-open handy bottom smallpadside" rel="tooltip" title="Show/hide empty records"></i>');
        },
        addAutoRefreshButton: function($table) {
            this.myDataTableToolbarAddHtml($table,
                '<i class="toggleAutoRefresh table-toolbar-icon bootstrap-glyph icon-pause handy smallpadside" rel="tooltip" title="Toggle auto-refresh"></i>');
        },
        /* fnConvertData takes the entries in collection (value, optionalKeyOrIndex) and returns a list
         * whose first element is the ID (hidden first column of table)
         * and other elements are the other columns in the table;
         * alternatively it can return null if the entry should be excluded
         * 
         * option refreshAllRows can be passed to force all rows to be re-rendered;
         * useful if rendering data may have changed even if value has not
         */ 
        updateMyDataTable: function(table, collection, fnConvertData, options) {
            if (table==null) return;
            if (options==null) options = {}
            var oldDisplayDataList = []
            try {
                oldDisplayDataList = table.dataTable().fnGetData();
            } catch (e) {
                // (used to) sometimes get error acessing column 1 of row 0, though table seems empty
                // caused by previous attempt to refresh from a closed view
                log("WARNING: could not fetch data; clearing")
                log(e)
                log(e.stack)
                table.dataTable().fnClearTable()
            }
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
                    if (options['refreshAllRows'] || !_.isEqual(v,oldProps[idx])) {
                        // update individual columns as values change
                        try {
                            table.fnUpdate( v, Number(prop), idx, false, false )
                        } catch (e) {
                            // sometimes get async errors
                            log("WARNING: cannot update row")
                            log(e)
                            log(e.stack)
                            log(v)
                            log(prop)
                            log(idx)
                        }
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
                try {
                    table.fnAddData( newDisplayData[prop] )
                } catch (e) {
                    // errors sometimes if we load async
                    log("WARNING: cannot add to row")
                    log(e)
                    log(e.stack)
                    log(prop)
                    log(newDisplayData[prop])
                }
            }
            try {
                // redraw, but keeping pagination
                table.fnStandingRedraw();
            } catch (e) {
                log("WARNING: could not redraw")
                log(e)
                log(e.stack)                
            }
            ViewUtils.cancelFadeOnceLoaded(table)
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
            ViewUtils.onTogglerClickElement($(event.currentTarget).closest(".toggler-header"));
        },
        onTogglerClickElement: function(root) {
            root.toggleClass("user-hidden");
            $(".toggler-icon", root).toggleClass("icon-chevron-left").toggleClass("icon-chevron-down");
            var next = root.next();
            if (root.hasClass("user-hidden")) {
                next.slideUp('fast');
            } else {
                next.slideDown('fast');
            }
        },
        updateTextareaWithData: function($div, data, showIfEmpty, doSlideDown, minPx, maxPx) {
            var $ta = $("textarea", $div);
            var show = showIfEmpty;
            if (data !== undefined) {
                $ta.val(data);
                show = true;
            } else {
                $ta.val("");
            }
            if (show) {
                ViewUtils.setHeightAutomatically($ta, minPx, maxPx, false)
                if (doSlideDown) { $div.slideDown(100); }
            } else {
                $div.hide();
            }
        },
        setHeightAutomatically: function($ta, minPx, maxPx, deferred) {
            var height = $ta.prop("scrollHeight");
            if ($ta.css("padding-top")) height -= parseInt($ta.css("padding-top"), 10)
            if ($ta.css("padding-bottom")) height -= parseInt($ta.css("padding-bottom"), 10)
//            log("scroll height "+height+" - old real height "+$ta.css("height"))
            if (height==0 && !deferred) {
                _.defer(function() { ViewUtils.setHeightAutomatically($ta, minPx, maxPx, true) })
            } else {
                height = Math.min(height, maxPx);
                height = Math.max(height, minPx);
                $ta.css("height", height);
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
        },
        // makes tooltips appear as white-on-black-bubbles rather than boring black-on-yellow-boxes
        // but NB if the html is updated the tooltip can remain visible until page refresh
        processTooltips: function($el) {
            $el.find('*[rel="tooltip"]').tooltip();
        },
        fadeToIndicateInitialLoad: function($el) {
            // in case the server response time is low, fade out while it refreshes
            // (since we can't show updated details until we've retrieved app + entity details)
            try {                
                $el.fadeTo(1000, 0.3)
//                    .queue(
//                        function() {
//                            // does nothing yet -- see comment in brooklyn.css on .view_not_available
//                            $el.append('<div class="view_not_available"></div>')
//                        });
                // above works to insert the div, though we don't have styling on it
                // but curiously it also causes the parent to go to opacity 0 !?! 
            } catch (e) {
                // ignore - normal during tests
            }
        },
        cancelFadeOnceLoaded: function($el) {
            try {
//                $el.children('.view_not_available').remove();
                $el.stop(true, false).fadeTo(200, 1);
            } catch (e) {
                // ignore - normal during tests
            }
        },
        
        
        
        // TODO the get and fetch methods below should possibly be on a BrooklynView prototype
        // see also notes in router.js
        // (perhaps as part of that introduce a callWithFixedDelay method which does the tracking, 
        // so we can cleanly unregister, and perhaps an onServerFailure method, and with that we 
        // could perhaps get rid of, or at least dramatically simplify, the get/fetch)
        
        /* variant of $.get with automatic failure handling and recovery;
         * options should be omitted except by getRepeatedlyWithDelay */
        get: function(view, url, success, options) {
            if (view.viewIsClosed) return ;
            
            if (!options) options = {}
            if (!options.count) options.count = 1
            else options.count++;
//          log("getting, count "+options.count+", delay "+period+": "+url)
            
            var disabled = (options['enablement'] && !options['enablement']()) 
                || !BrooklynConfig.refresh
            if (options.count > 1 && disabled) {
                // not enabled, just requeue
                if (options['period']) 
                    setTimeout(function() { ViewUtils.get(view, url, success, options)}, options['period'])
                return;
            }
            
            /* inspects the status object returned from an ajax call in a view;
             * if not valid, it fades the view and increases backoff delays and resubmits;
             * if it is valid, it returns true so the caller can continue
             * (restoring things such as the view, timer, etc, if they were disabled);
             * 
             * takes some of the options as per fetchRepeatedlyWithDelay
             * (though they are less well tested here)
             * 
             * note that the status text object is rarely useful; normally the fail(handler) is invoked,
             * as above (#get)
             */
            var checkAjaxStatusObject = function(status, view, options) {
                if (view.viewIsClosed) return false;
                if (status == "success" || status == "notmodified") {
                    // unfade and restore
                    if (view._loadingProblem) {
                        log("getting view data is back to normal - "+url)
                        log(view)
                        view._loadingProblem = false;
                        
                        var fadeTarget = view.$el;
                        if ("fadeTarget" in options) {
                            fadeTarget = options["fadeTarget"]
                        }
                        if (fadeTarget) ViewUtils.cancelFadeOnceLoaded(fadeTarget)
                        
                        if (options['originalPeriod']) 
                            options.period = options['originalPeriod']; 
                    }
                    
                    return true;
                }
                if (status == "error" || status == "timeout" || status == "parsererror") {
                    // fade and log problem
                    if (!view._loadingProblem) {
                        log("error getting view data from "+url+" - is the server reachable?")
                        view._loadingProblem = true;
                    }
                    // fade the view, on error
                    var fadeTarget = view.$el;
                    if ("fadeTarget" in options) {
                        fadeTarget = options["fadeTarget"]
                    }
                    if (fadeTarget) ViewUtils.fadeToIndicateInitialLoad(fadeTarget)

                    if (options['period']) {
                        if (!options['originalPeriod']) options.originalPeriod = options['period'];
                        var period = options['period'];
                        
                        // attempt exponential backoff up to every 15m
                        period *= 2;
                        var max = (options['backoffMaxPeriod'] || 15*60*1000);
                        if (period > max) period = max;
                        options.period = period
                        setTimeout(function() { ViewUtils.get(view, url, success, options)}, period)
                    } 
                    
                    return false;
                }
                return true;
            }
            
            return $.get(url, function(data, status) {
                if (!checkAjaxStatusObject(status, view, options)) {
                    return;
                }
                if (success) success(data);
                if (options['period']) 
                    setTimeout(function() { ViewUtils.get(view, url, success, options)}, options['period'])
            }).fail(function() {
                checkAjaxStatusObject("error", view, options)
            })
        },
        /** invokes a get against the given url repeatedly, with fading and backoff on failures,
         * cf fetchRepeatedlyWithDelay, but here the user's callback function is invoked on success
         */
        getRepeatedlyWithDelay: function(view, url, success, options) {
            if (!options) options = {}
            if (!options['period']) options.period = 3000
            ViewUtils.get(view, url, success, options)
        },
        /* invokes fetch on the model, associated with the view.
         * automatically closes when view closes, 
         * and fades display and exponentially-backs off on problems.
         * options include:
         * 
         *   enablement (function returning t/f whether the invocation is enabled)
         *   period (millis, currently 3000 = 3s default);
         *   originalPeriod (millis, becomes the period if successful; primarily for internal use);
         *   backoffMaxPeriod (millis, max time to wait between retries, currently 15*60*1000 = 10m default);
         *    
         *   doitnow (if true, kicks off a run immediately, else only after the timer)
         *   
         *   fadeTarget (jquery element to fade; defaults to view.$el; null can be set to prevent fade);
         *   
         *   fetchOptions (additional options to pass to fetch; however success and error should not be present);
         *   success (function to invoke on success, before re-queueing);
         *   error (optional function to invoke on error, before requeueing);
         */
        fetchRepeatedlyWithDelay: function(view, model, options) {
            if (view.viewIsClosed) return;
            
            if (!options) options = {}
            if (!options.count) options.count = 1
            else options.count++;
            
            var period = options['period'] || 3000
            var originalPeriod = options['originalPeriod'] || period
//            log("fetching, count "+options.count+", delay "+period+": "+model.url)
            
            var fetcher = function() {
                if (view.viewIsClosed) return;
                var disabled = (options['enablement'] && !options['enablement']()) 
                    || !BrooklynConfig.refresh
                if (options.count > 1 && disabled) {
                    // not enabled, just requeue
                    ViewUtils.fetchRepeatedlyWithDelay(view, model, options);
                    return;
                }
                var fetchOptions = options['fetchOptions'] ? _.clone(options['fetchOptions']) : {}
                fetchOptions.success = function(modelR,response,optionsR) {
                        var fn = options['success']
                        if (fn) fn(modelR,response,optionsR);
                        if (view._loadingProblem) {
                            log("fetching view data is back to normal - "+model.url)
                            view._loadingProblem = false;
                            
                            var fadeTarget = view.$el;
                            if ("fadeTarget" in options) {
                                fadeTarget = options["fadeTarget"]
                            }
                            if (fadeTarget) ViewUtils.cancelFadeOnceLoaded(fadeTarget)
                        }
                        options.period = originalPeriod;
                        ViewUtils.fetchRepeatedlyWithDelay(view, model, options);
                }
                fetchOptions.error = function(modelR,response,optionsR) {
                        var fn = options['error']
                        if (fn) fn(modelR,response,optionsR);
                        if (!view._loadingProblem) {
                            log("error fetching view data from "+model.url+" - is the server reachable?")
                            log(response)
                            view._loadingProblem = true;
                        }
                        // fade the view, on error
                        var fadeTarget = view.$el;
                        if ("fadeTarget" in options) {
                            fadeTarget = options["fadeTarget"]
                        }
                        if (fadeTarget) ViewUtils.fadeToIndicateInitialLoad(fadeTarget)
                        
                        // attempt exponential backoff up to every 15m
                        period *= 2;
                        var max = (options['backoffMaxPeriod'] || 15*60*1000);
                        if (period > max) period = max;
                        options = _.clone(options)
                        options.originalPeriod = originalPeriod;
                        options.period = period;
                        ViewUtils.fetchRepeatedlyWithDelay(view, model, options);
                };
                model.fetch(fetchOptions)
            };
            if (options['doitnow']) {
                options.doitnow = false;
                fetcher();
            } else {
                setTimeout(fetcher, period);
            }
        },
        computeStatusIcon: function(serviceUp, lifecycleState) {
            if (serviceUp===false || serviceUp=="false") serviceUp=false;
            else if (serviceUp===true || serviceUp=="true") serviceUp=true;
            else {
                if (serviceUp!=null && serviceUp !== "" && serviceUp !== undefined) {
                    log("Unknown 'serviceUp' value:")
                    log(serviceUp)
                }
                serviceUp = null;
            }
            var PATH = "/assets/img/";
            
            if (lifecycleState=="running") {
                if (serviceUp==false) return PATH+"icon-status-running-onfire.png";
                return PATH+"icon-status-running.png";
            }
            if (lifecycleState=="stopped" || lifecycleState=="created") {
                if (serviceUp==true) return PATH+"icon-status-stopped-onfire.png";
                return PATH+"icon-status-stopped.png";
            }
            if (lifecycleState=="starting") {
                return PATH+"icon-status-starting.gif";
            }
            if (lifecycleState=="stopping") {
                return PATH+"icon-status-stopping.gif";
            }
            if (lifecycleState=="on-fire" || /* just in case */ lifecycleState=="onfire") {
                return PATH+"icon-status-onfire.png";
            }
            if (lifecycleState!=null && lifecycleState !== "" && lifecycleState !== undefined) {
                log("Unknown 'lifecycleState' value:")
                log(lifecycleState)
                return null;
            }
            // no lifecycle state, rely on serviceUp
            if (serviceUp) return PATH+"icon-status-running.png"; 
            if (serviceUp===false) return PATH+"icon-status-stopped.png";
            // no status info at all
            return null;
        }
    };
    return ViewUtils;
});
