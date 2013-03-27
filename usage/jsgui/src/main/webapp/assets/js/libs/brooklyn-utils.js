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

function log(args) {
    if (typeof window.console != 'undefined') {
        console.log(args);
        if (arguments.length>1) {
            for (i=1; i<arguments.length; i++)
                console.log(arguments[i])
        }
    }
}

function setVisibility(obj, visible) {
    if (visible) obj.show()
    else obj.hide()
}

/** preps data for output */
function prep(s) {
    if (s==null) return "";
    return _.escape(s);
}

function roundIfNumberToNumDecimalPlaces(v, mantissa) {
    if (typeof v !== 'number')
        return v;
    
    log("rounding")
    log(v)
    if (isWholeNumber(v))
        return Math.round(v);
    
    var vk = v, xp = 1;
    for (i=0; i<mantissa; i++) {
        vk *= 10;
        xp *= 10;
        if (isWholeNumber(vk)) {
            log("bailing")
            log(vk)            
            log(Math.round(vk)/xp);
            return Math.round(vk)/xp;
        }
    }
    log("toFixed")
    log(Number(v.toFixed(mantissa)))
    return Number(v.toFixed(mantissa))
}

function isWholeNumber(v) {
    return (Math.abs(Math.round(v) - v) < 0.000000000001);
}

