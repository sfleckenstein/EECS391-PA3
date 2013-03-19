import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.util.Direction;


public class Deposit extends Act {

	private int amount;
	private Direction directionToTownHall;
	private ResourceType type;
	
	public Deposit(int amount, Direction dirToTH, ResourceType type) {
		this.amount = amount;
		this.directionToTownHall = dirToTH;
		this.type = type;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public Direction getDirectionToTownHall() {
		return directionToTownHall;
	}
	
	public ResourceType getResourceType() {
		return type;
	}
	
	public String getTypeString() {
		String str;
		switch(type) {
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
	
	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEDEPOSIT, directionToTownHall);
	}


}
