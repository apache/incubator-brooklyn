db = db.getMongo().getDB("foo");
db.products.insert({name:"bar", type:"baz"});
db.products.insert({name:"My Product", type:"Gizmo"});