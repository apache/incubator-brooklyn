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

    // TODO: Unused. Necessary?
    function count_occurrences(string, subString, allowOverlapping) {
        string+=""; subString+="";
        if(subString.length<=0) return string.length+1;

        var n=0, pos=0;
        var step=(allowOverlapping)?(1):(subString.length);

        while(true){
            pos=string.indexOf(subString,pos);
            if(pos>=0){ n++; pos+=step; } else break;
        }
        return(n);
    }

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
        if (typeof v !== 'number')
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


