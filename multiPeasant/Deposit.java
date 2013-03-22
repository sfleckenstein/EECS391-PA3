import edu.cwru.sepia.environment.model.state.ResourceType;

public class Deposit extends Act {

	private int depositID;
	private ResourceType resource;
	private int amount;
	
	public Deposit(int depositID, ResourceType resource, int amount) {
		this.depositID = depositID;
		this.resource = resource;
		this.amount = amount;
	}

	public int getDepositID() {
		return depositID;
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
