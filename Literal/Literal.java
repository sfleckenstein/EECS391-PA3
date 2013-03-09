import java.awt.Point;

import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;


public class Literal {
	
	/**
	 * 
	 * @param state - The StateView you are concerned with.
	 * @param objectID - The ID of the object you are concerned with.
	 * @param p - The location you are concerned with.
	 * @return True if the specified object is at point p.
	 */
	public static boolean At(StateView state, int objectID, Point p) {
		Point compare = new Point();
		if(state.isUnitAt((int)p.getX(), (int)p.getY())) {
			UnitView unit = state.getUnit(objectID);
			compare.x = unit.getXPosition();
			compare.y = unit.getYPosition();
			
			return compare.equals(p);
		} else if(state.isResourceAt((int)p.getX(), (int)p.getY())) {
			ResourceView resource = state.getResourceNode(objectID);
			compare.x = resource.getXPosition();
			compare.y = resource.getYPosition();
			
			return compare.equals(p);
		}
		return false;
	}
	
	/**
	 * 
	 * @param state - The StateView you are concerned with.
	 * @param unitID - The ID of the unit you are concerned with.
	 * @param resource - The resource you are concerned with.
	 * @return True if the specified unit is holding the specified resource.
	 */
	public static boolean Has(StateView state, int unitID, ResourceType resource) {
		return state.getUnit(unitID).getCargoType().equals(resource);
	}

}
