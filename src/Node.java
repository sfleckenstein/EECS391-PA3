import edu.cwru.sepia.environment.model.state.State.StateView;


public class Node implements Comparable<Object>{
	
	private StateView state;
	private Node parent;
	private Act toState;
	private int costToNode;
	private int peasantX;
	private int peasantY;
	private int costToGoal;
	//TODO figure out how to represent literals
	//possibly another abstract class
	//private ArrayList<Literal> stateLits; 

	/**
	 * 
	 * @param state - The StateView of the node
	 * @param parent - Parent node
	 * @param toState - The Act to get to this node
	 * @param costToNode - The total cost to get to this node
	 * @param costToGoal - Estimated cost to goal
	 * @param peasantX - Peasant's x coordinate
	 * @param peasantY - Peasant's y coordinate
	 */
	public Node(StateView state, Node parent, Act toState, int costToNode, int costToGoal, int peasantX, int peasantY){
		this.state = state;
		this.parent = parent;
//		this.children = children;
		this.toState = toState;
		this.costToNode = costToNode;
		this.costToGoal = costToGoal;
		this.peasantX = peasantX;
		this.peasantY = peasantY;
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
	
	public int getPeasantX() {
		return peasantX;
	}
	
	public int getPeasantY() {
		return peasantY;
	}
	
	public int getCostToNode() {
		return costToNode;
	}

	public void setCostToNode(int costToNode) {
		this.costToNode = costToNode;
	}
	
	public int getCostToGoal() {
		return costToGoal;
	}
	
	public Act getToState() {
		return toState;
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

}
