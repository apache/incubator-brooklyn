db = db.getMongo().getDB("foo");
db.products.insert({name:"bar", type:"baz"});
if (typeof loopCount == "undefined") loopCount = 5;
for (var i = 1; i < loopCount; i++) { 
	db.products.insert({name:"My Product #" + i, type:"Gizmo"});
}
