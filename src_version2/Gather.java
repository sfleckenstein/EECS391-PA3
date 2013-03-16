import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.util.Direction;


public class Gather extends Act {

	private int amount;
	private Direction directionToResource;
	
	public Gather(int amount, Direction dirToResource) {
		this.amount = amount;
		this.directionToResource = dirToResource;
	}
	
	public Direction getDirectionToResource() {
		return directionToResource;
	}
	
	public int getAmount() {
		return amount;
	}
	
	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEGATHER, directionToResource);
	}
}
