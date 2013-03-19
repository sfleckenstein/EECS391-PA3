import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.util.Direction;


public class Gather extends Act {

	private int amount;
	private Direction directionToResource;
	private ResourceType type;
	
	public Gather(int amount, Direction dirToResource, ResourceType type) {
		this.amount = amount;
		this.directionToResource = dirToResource;
		this.type = type;
	}
	
	public Direction getDirectionToResource() {
		return directionToResource;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public ResourceType getType() {
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
		return new DirectedAction(peasantId, ActionType.PRIMITIVEGATHER, directionToResource);
	}
}
