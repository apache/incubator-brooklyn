package brooklyn.management.webconsole

import grails.converters.JSON

import java.util.Collection

import brooklyn.entity.basic.BasicEntitySummary
import brooklyn.entity.Entity
import brooklyn.entity.EntitySummary
import brooklyn.management.ManagementContext

class EntityController {

	class TestManagementApi implements ManagementContext {
		Collection<EntitySummary> getApplicationSummaries() {
			return new ArrayList<EntitySummary>();
		}

		Collection<EntitySummary> getEntitySummariesInApplication(String id) {
			return new ArrayList<EntitySummary>();
		}

		Collection<EntitySummary> getAllEntitySummaries() {
			return [
				getHackyEntitySummary("a1", "Application", []),
				getHackyEntitySummary("t1", "tomcat tier 1", ["a1"]),
				getHackyEntitySummary("tcc1a", "tomcat cluster 1a", ["t1"]),
				getHackyEntitySummary("tc1a1", "tomcat node 1a.1", ["tcc1a"]),
				getHackyEntitySummary("tc1a2", "tomcat node 1a.2", ["tcc1a"]),
				getHackyEntitySummary("tc1a3", "tomcat node 1a.3", ["tcc1a"]),
				getHackyEntitySummary("tc1a4", "tomcat node 1a.4", ["tcc1a"]),
				getHackyEntitySummary("tcc1b", "tomcat cluster 1b", ["t1"]),
				getHackyEntitySummary("tc1b1", "tomcat node 1b.1", ["tcc1b"]),
				getHackyEntitySummary("tc1b2", "tomcat node 1b.2", ["tcc1b"]),
				getHackyEntitySummary("tc1b3", "tomcat node 1b.3", ["tcc1b"]),
				getHackyEntitySummary("tc1b4", "tomcat node 1b.4", ["tcc1b"]),
				getHackyEntitySummary("d1", "data tier 1", ["a1"]),
				getHackyEntitySummary("dbc1", "data cluster 1a", ["d1"
					
					]),
				getHackyEntitySummary("db1", "data node 1a.1", ["dbc1"]),
				getHackyEntitySummary("db2", "data node 1a.2", ["dbc1"]),
				getHackyEntitySummary("db3", "data node 1a.3", ["dbc1"]),
			];
		}
		
		Entity getEntity(String id) {
			null;
		}

		EntitySummary getHackyEntitySummary(String id, displayName, ArrayList<String> groups) {
			return new BasicEntitySummary(id, displayName, "app1", groups);
		}
	}
	
	def index = {
	}
	
	public class JsTreeThingy implements Serializable {
		public long id;
		public Map<String, String> data = [:];
		public List<JsTreeThingy> children = null;
		public String state = "open";

		public JsTreeThingy(long id, String name, List<JsTreeThingy> children) {
			this.id = id;
			this.data.put("title", name)
			if (children.size() == 0) {
				this.state = "closed"
			} else {
				this.children = children
			}
		}
	}
	
	def list = {
	
		render new JsTreeThingy(1, "Application", [
			new JsTreeThingy(2, "tomcat tier 1", [
				new JsTreeThingy(3, "tomcat cluster 1a", [
					new JsTreeThingy(4, "tomcat node 1a.1", []),
					new JsTreeThingy(5, "tomcat node 1a.2", []),
					new JsTreeThingy(6, "tomcat node 1a.3", []),
					new JsTreeThingy(7, "tomcat node 1a.4", [])
				]),
				new JsTreeThingy(8, "tomcat cluster 1b", [
					new JsTreeThingy(9, "tomcat node 1b.1", []),
					new JsTreeThingy(10, "tomcat node 1b.2", []),
					new JsTreeThingy(11, "tomcat node 1b.3", []),
					new JsTreeThingy(12, "tomcat node 1b.4", [])
				]),
			]),
			new JsTreeThingy(13, "data tier 1", [
				new JsTreeThingy(14, "data cluster 1a", [
					new JsTreeThingy(15, "data node 1a.1", []),
					new JsTreeThingy(16, "data node 1a.2", []),
					new JsTreeThingy(17, "data node 1a.3", [])
				])
			])
		]) as JSON
		
			
//		render new TestManagementApi().getAllEntitySummaries() as JSON;
//		render "[{\"data\" : \"Ajax node\", \"children\" : [\"A\", \"B\"]}]"
	}
}

