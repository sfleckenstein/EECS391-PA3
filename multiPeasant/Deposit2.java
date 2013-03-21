import edu.cwru.sepia.environment.model.state.ResourceType;

public class Deposit2 extends Act {

	private int depositID1;
	private int depositID2;
	private ResourceType resource1;
	private ResourceType resource2;
	private int amount1;
	private int amount2;
	
	public Deposit2(int depositID1, ResourceType resource1, int amount1,
			int depositID2, ResourceType resource2, int amount2) {
		this.depositID1 = depositID1;
		this.depositID2 = depositID2;
		this.resource1 = resource1;
		this.resource2 = resource2;
		this.amount1 = amount1;
		this.amount2 = amount2;
	}

	public int getDepositID(int idNum) {
		switch(idNum) {
		case 1:
			return depositID1;
		case 2:
			return depositID2;
		}
		return -1;
	}
	
	public ResourceType getResource(int idNum) {
		switch(idNum) {
		case 1:
			return resource1;
		case 2:
			return resource2;
		}
		return null;
	}
	
	public int getAmount(int idNum) {
		switch(idNum) {
		case 1:
			return amount1;
		case 2:
			return amount2;
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