import edu.cwru.sepia.environment.model.state.ResourceType;

public class Gather3 extends Act {

	private int gatherID1;
	private int gatherID2;
	private int gatherID3;
	private ResourceType resource1;
	private ResourceType resource2;
	private ResourceType resource3;
	private int amount1;
	private int amount2;
	private int amount3;
	
	public Gather3(int gatherID1, ResourceType resource1, int amount1,
			int gatherID2, ResourceType resource2, int amount2,
			int gatherID3, ResourceType resource3, int amount3) {
		this.gatherID1 = gatherID1;
		this.gatherID2 = gatherID2;
		this.gatherID3 = gatherID3;
		this.resource1 = resource1;
		this.resource2 = resource2;
		this.resource3 = resource3;
		this.amount1 = amount1;
		this.amount2 = amount2;
		this.amount3 = amount3;
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
	
	public ResourceType getResource(int idNum) {
		switch(idNum) {
		case 1:
			return resource1;
		case 2:
			return resource2;
		case 3:
			return resource3;
		}
		return null;
	}
	
	public int getAmount(int idNum) {
		switch(idNum) {
		case 1:
			return amount1;
		case 2:
			return amount2;
		case 3:
			return amount3;
		}
		return -1;
	}
	
//	public String getResourceString() {
//		String str;
//		switch(resource) {
//		case GOLD:
//			str = "GOLD";
//			break;
//		case WOOD:
//			str = "WOOD";
//			break;
//		default:
//			str = "";
//		}
//		return str;
//	}
}