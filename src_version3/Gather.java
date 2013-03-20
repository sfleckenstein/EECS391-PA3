import edu.cwru.sepia.environment.model.state.ResourceType;

public class Gather extends Act {

	private int gatherID;
	private int amount;
	private ResourceType resource;
	
	public Gather(int gatherID, ResourceType resource, int amount) {
		this.gatherID = gatherID;
		this.resource = resource;
		this.amount = amount;
	}

	public int getGatherID() {
		return gatherID;
	}
	
	public ResourceType getResource() {
		return resource;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public String getResourceString() {
		String str;
		switch(resource) {
		case GOLD:
			str = "GOLD";
			break;
		case WOOD:
			str = "WOOD";
			break;
		default:
			str = "";
		}
		return str;
	}
}
