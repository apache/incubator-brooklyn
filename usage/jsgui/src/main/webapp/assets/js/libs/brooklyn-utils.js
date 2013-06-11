define([
    'underscore'
], function (_) {

    var Util = {};

    Util.log = function (args) {
        if (!_.isUndefined(window.console)) {
            console.log(args);
            if (arguments.length > 1) {
                for (var i=1; i < arguments.length; i++) {
                    console.log(arguments[i]);
                }
            }
        }
    };

    // TODO: Also rename
    /** preps data for output */
    Util.prep = function (s) {
        if (s==null) return "";
        return _.escape(s);
    };

    function isWholeNumber(v) {
        return (Math.abs(Math.round(v) - v) < 0.000000000001);
    }

    Util.roundIfNumberToNumDecimalPlaces = function (v, mantissa) {
        if (!_.isNumber(v) || mantissa < 0)
            return v;

        if (isWholeNumber(v))
            return Math.round(v);

        var vk = v, xp = 1;
        for (var i=0; i < mantissa; i++) {
            vk *= 10;
            xp *= 10;
            if (isWholeNumber(vk)) {
                return Math.round(vk)/xp;
            }
        }
        return Number(v.toFixed(mantissa))
    };

    return Util;

});


