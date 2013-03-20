import edu.cwru.sepia.environment.model.state.ResourceType;

public class Has extends Literal {
	
	private int objectID;
	private ResourceType resource;
	private int amount;

	public Has(int objectID, ResourceType resource, int amount) {
		this.objectID = objectID;
		this.resource = resource;
		this.amount = amount;
	}

	public int getObjectID() {
		return objectID;
	}
	
	public ResourceType getResource() {
		return resource;
	}
	
	public int getAmount() {
		return amount;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class Has")) {
			return false;
		}
		Has h = (Has)o;
		if(h.objectID == this.objectID && h.resource.equals(this.resource) && h.amount == this.amount) {
			return true;
		}
		return false;
	}
}
