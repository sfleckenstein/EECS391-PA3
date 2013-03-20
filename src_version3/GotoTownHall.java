
public class GotoTownHall extends Act {
	
	private int moverID;	
	
	public GotoTownHall(int moverID) {
		this.moverID = moverID;
	}

//	@Override
//	public Action act(int peasantId) {
//		return new DirectedAction(peasantId, ActionType.PRIMITIVEMOVE, direction);
//	}
	
	public int getMoverID() {
		return moverID;
	}
	
//	public String getDirectionString() {
//		String str;
//		switch(direction) {
//		case NORTH:
//			str = "NORTH";
//			break;
//		case EAST:
//			str = "EAST";
//			break;
//		case SOUTH:
//			str = "SOUTH";
//			break;
//		case WEST:
//			str = "WEST";
//			break;
//		default:
//			str = "";
//		}
//		return str;
//	}

}
