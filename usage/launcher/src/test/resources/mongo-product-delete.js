db = db.getMongo().getDB("foo");
db.products.remove({name:"bar"});