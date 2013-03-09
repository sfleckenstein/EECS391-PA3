import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.util.Direction;


public class GatherGold extends Act {

	private int amount;
	private Direction directionToGold;
	
	public GatherGold(int amount, Direction dirToGold) {
		this.amount = amount;
		this.directionToGold = dirToGold;
	}
	
	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEGATHER, directionToGold);
	}
}
