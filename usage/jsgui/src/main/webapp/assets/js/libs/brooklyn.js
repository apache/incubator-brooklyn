/** Client configuration. */
define([
    "brooklyn-view", "brooklyn-utils"
], function (BrooklynViews, BrooklynUtils) {

    /**
     * Makes the console API safe to use:
     *  - Stubs missing methods to prevent errors when no console is present.
     *  - Exposes a global `log` function that preserves line numbering and formatting.
     *
     * Idea from https://gist.github.com/bgrins/5108712
     */
    (function () {
        var noop = function () {},
            consoleMethods = [
                'assert', 'clear', 'count', 'debug', 'dir', 'dirxml', 'error',
                'exception', 'group', 'groupCollapsed', 'groupEnd', 'info', 'log',
                'markTimeline', 'profile', 'profileEnd', 'table', 'time', 'timeEnd',
                'timeStamp', 'trace', 'warn'
            ],
            length = consoleMethods.length,
            console = (window.console = window.console || {});

        while (length--) {
            var method = consoleMethods[length];

            // Only stub undefined methods.
            if (!console[method]) {
                console[method] = noop;
            }
        }

        if (Function.prototype.bind) {
            window.log = Function.prototype.bind.call(console.log, console);
        } else {
            window.log = function () {
                Function.prototype.apply.call(console.log, console, arguments);
            };
        }
    })();

    var Brooklyn = {
        refresh: true,
        toggleRefresh: function () {
            this.refresh = !this.refresh;
            return this.refresh;
        },
        view: BrooklynViews,
        util: BrooklynUtils
    };

    return Brooklyn;
});
