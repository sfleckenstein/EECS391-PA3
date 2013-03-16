import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.util.Direction;


public class Deposit extends Act {

	private int amount;
	private Direction directionToTownHall;
	
	public Deposit(int amount, Direction dirToTH) {
		this.amount = amount;
		this.directionToTownHall = dirToTH;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public Direction getDirectionToTownHall() {
		return directionToTownHall;
	}
	
	@Override
	public Action act(int peasantId) {
		return new DirectedAction(peasantId, ActionType.PRIMITIVEDEPOSIT, directionToTownHall);
	}


}
