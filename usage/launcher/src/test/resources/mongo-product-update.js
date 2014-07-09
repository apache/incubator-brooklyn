db = db.getMongo().getDB("foo");
db.products.update({name:"My Product"}, {$set: {price:"$100"}}, {multi: true, upsert: false});