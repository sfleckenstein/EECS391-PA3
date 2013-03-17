import edu.cwru.sepia.environment.model.state.ResourceType;


public class Has extends Literal {
	
	private int holderID;
	private ResourceType resource;
	
	/**
	 * 
	 * @param holderID - ID of the holder
	 * @param resource - Resource they are holding
	 */
	public Has(int holderID, ResourceType resource) {
		this.holderID = holderID;
		this.resource = resource;
	}

	public int getHolderID() {
		return holderID;
	}
	
	public ResourceType getToHold() {
		return resource;
	}

	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class Has")) {
			return false;
		}
		Has h = (Has)o;
		//TODO this probably isn't working
		if(h.holderID == this.holderID && h.resource.equals(this.resource)) {
			return true;
		}
		return false;
	}
}
