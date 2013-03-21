import edu.cwru.sepia.environment.model.state.ResourceType;

public class AtResource extends Literal {

	private int objectID;
	private ResourceType resource;
	
	public AtResource(int objectID, ResourceType resource) {
		this.objectID = objectID;
		this.resource = resource;
	}
	
	public int getObjectID() {
		return objectID;
	}
	
	public ResourceType getType() {
		return resource;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class AtResource")) {
			return false;
		}
		AtResource a = (AtResource)o;
		if(a.objectID == this.objectID && a.resource.equals(this.resource)) {
			return true;
		}
		return false;
	}
	
}
