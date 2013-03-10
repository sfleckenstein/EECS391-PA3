import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;



public class Node {
	
	private Node parent;
//	private ArrayList<Node> children = new ArrayList<Node>();
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
	 * @param parent - Parent node
	 * @param toState - The action used to get to this node.
	 * @param costToNode - The total cost to get to this node.
	 * @param peasant - Info on the peasant.
	 */
	public Node(Node parent, Act toState, int costToNode, int costToGoal, int peasantX, int peasantY){
		this.parent = parent;
//		this.children = children;
		this.toState = toState;
		this.costToNode = costToNode;
		this.costToGoal = costToGoal;
		this.peasantX = peasantX;
		this.peasantY = peasantY;
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

}
