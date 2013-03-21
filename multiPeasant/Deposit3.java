import edu.cwru.sepia.environment.model.state.ResourceType;

public class Deposit3 extends Act {

	private int depositID1;
	private int depositID2;
	private int depositID3;
	private ResourceType resource;
	private int amount;
	
	public Deposit3(int depositID1, int depositID2, int depositID3, ResourceType resource, int amount) {
		this.depositID1 = depositID1;
		this.depositID2 = depositID2;
		this.depositID3 = depositID3;
		this.resource = resource;
		this.amount = amount;
	}

	public int getDepositID(int idNum) {
		switch(idNum) {
		case 1:
			return depositID1;
		case 2:
			return depositID2;
		case 3:
			return depositID3;
		}
		return -1;
	}
	
	public ResourceType getResource() {
		return resource;
	}
	
	public int getAmount() {
		return amount;
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