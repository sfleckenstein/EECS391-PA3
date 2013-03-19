import edu.cwru.sepia.environment.model.state.ResourceType;


public class Has extends Literal {
	
	private int holderID;
	private ResourceType resource;
	private int amount;
	
	/**
	 * 
	 * @param holderID - ID of the holder
	 * @param resource - Resource they are holding
	 */
	public Has(int holderID, ResourceType resource, int amount) {
		this.holderID = holderID;
		this.resource = resource;
		this.amount = amount;
	}

	public int getHolderID() {
		return holderID;
	}
	
	public ResourceType getResource() {
		return resource;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public void setAmount(int amount) {
		this.amount = amount;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class Has")) {
			return false;
		}
		Has h = (Has)o;
		if(h.holderID == this.holderID && h.resource.equals(this.resource) && h.amount == this.amount) {
			return true;
		}
		return false;
	}
}
