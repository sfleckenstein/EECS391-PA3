import java.awt.Point;
import java.util.ArrayList;

import edu.cwru.sepia.environment.model.state.State.StateView;


public class Node implements Comparable<Object>{
	
	private StateView state;
	private Node parent;
	private Act toState;
	private ArrayList<Literal> stateLits;
	private int costToNode;
	private Point goal;
	private int costToGoal;
	private Point peasantLoc;

	/**
	 * 
	 * @param state - The state of the node
	 * @param parent - The parent of the node
	 * @param toState - The Act made to get to the state
	 * @param stateLits - The state literals
	 * @param costToNode - The total cost to get to the node
	 * @param goal - The location of the goal
	 * @param costToGoal - The estimated cost to the goal
	 * @param peasantLoc - The location of the peasant
	 */
	public Node(StateView state, Node parent, Act toState, ArrayList<Literal> stateLits, 
			int costToNode, Point goal, int costToGoal, Point peasantLoc) {
		this.state = state;
		this.parent = parent;
		this.toState = toState;
		this.stateLits = stateLits;
		this.costToNode = costToNode;
		this.goal = goal;
		this.costToGoal = costToGoal;
		this.peasantLoc = new Point((int)peasantLoc.getX(), (int)peasantLoc.getY());
	}
	
	public StateView getState() {
		return state;
	}
	
	public Node getParentNode() {
		return parent;
	}
	
	public void setParentNode(Node parent) {
		this.parent = parent;
	}
	
	public int getCostToNode() {
		return costToNode;
	}

	public void setCostToNode(int costToNode) {
		this.costToNode = costToNode;
	}
	
	public Point getGoal() {
		return goal;
	}
	
	public void setGoal(Point goal) {
		this.goal = goal;
	}
	
	public int getCostToGoal() {
		return costToGoal;
	}
	
	public Act getToState() {
		return toState;
	}
	
	public ArrayList<Literal> getStateLits() {
		return stateLits;
	}
	
	/**
	 * 
	 * @param toFind - The literal you are searching for
	 * @return True if the node contains the queried literal.
	 */
	public boolean containsLit(Literal toFind) {
		String toFindClass = toFind.getClass().toString();
		for(Literal stateLit : stateLits) {
			String classType = stateLit.getClass().toString();
			if(classType.equals(toFindClass)) {
				if(stateLit.equals(toFind)){
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public int compareTo(Object o) {
		Node n = (Node)o;
		if(this.costToNode + this.costToNode < n.costToNode + n.costToNode) {
			return -1;
		} else if(this.costToNode + this.costToNode == n.costToNode + n.costToNode) {
			return 0;
		} else {
			return 1;
		}
	}

	public Point getPeasantLoc() {
		for(Literal lit : stateLits) {
			if(lit.getClass().toString().equals("class At")
					&& ((At)lit).getObjectID() == 1) {	//TODO 1 should be change to peasantIds.get(0) or something like that
				return ((At)lit).getPosition();
			}
		}
		return peasantLoc;
	}

	public void setPeasantLoc(Point peasantLoc) {
		this.peasantLoc = peasantLoc;
	}

}
