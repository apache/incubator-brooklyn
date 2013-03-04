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

function log(obj) {
    if (typeof window.console != 'undefined') {
        console.log(obj);
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
    if (Math.round(v)==v)
        return v;
    
    var vk = v;
    for (i=0; i<mantissa; i++) {
        vk *= 10;
        log(vk)
        if (Math.round(vk)==vk)
            return vk;
    }
    // rounding needed
    vk = Math.round(vk);
    for (i=0; i<mantissa; i++)
        vk /= 10;
    return vk;
}
