import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.util.Direction;


public class Move extends Act {
	
	private Direction direction;
	
	public Move(Direction direction) {
		this.direction = direction;
	}

	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEMOVE, direction);
	}
	
	public Direction getDirection() {
		return direction;
	}

}
