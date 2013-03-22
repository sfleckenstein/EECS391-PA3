import edu.cwru.sepia.environment.model.state.ResourceType;

public class Gather3 extends Act {

	private int gatherID1;
	private int gatherID2;
	private int gatherID3;
	private ResourceType resource;
	private int amount;
	
	public Gather3(int gatherID1, int gatherID2, int gatherID3, ResourceType resource, int amount) {
		this.gatherID1 = gatherID1;
		this.gatherID2 = gatherID2;
		this.gatherID3 = gatherID3;
		this.resource = resource;
		this.amount = amount;
	}

	public int getGatherID(int idNum) {
		switch(idNum) {
		case 1:
			return gatherID1;
		case 2:
			return gatherID2;
		case 3:
			return gatherID3;
		}
		return -1;
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