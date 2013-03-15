import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.util.Direction;


public class GatherWood extends Act {

	private int amount;
	private Direction directionToWood;
	
	@Deprecated
	public GatherWood(int amount, Direction dirToWood) {
		this.amount = amount;
		this.directionToWood = dirToWood;
	}
	
	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEGATHER, directionToWood);
	}

}
