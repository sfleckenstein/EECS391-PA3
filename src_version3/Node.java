import java.util.ArrayList;

public class Node implements Comparable<Object>{
	
	private Node parent;
	private Act toState;
	private ArrayList<Literal> stateLits;
	private int costToNode;
	private int costToGoal;

	/**
	 * 
	 * @param parent - The parent of the node
	 * @param toState - The Act made to get to the state
	 * @param stateLits - The state literals
	 * @param costToNode - The total cost to get to the node
	 * @param costToGoal - The estimated cost to the goal
	 */
	public Node(Node parent, Act toState, ArrayList<Literal> stateLits, 
			int costToNode, int costToGoal) {
		this.parent = parent;
		this.toState = toState;
		this.stateLits = stateLits;
		this.costToNode = costToNode;
		this.costToGoal = costToGoal;
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
	
	public int getCostToGoal() {
		return costToGoal;
	}
	
	public Act getToState() {
		return toState;
	}
	
	public ArrayList<Literal> getStateLits() {
		return stateLits;
	}
	

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
		if(this.costToGoal + this.costToNode < n.costToGoal + n.costToNode) {
			return -1;
		} else if(this.costToGoal + this.costToNode == n.costToGoal + n.costToNode) {
			return 0;
		} else {
			return 1;
		}
	}

}
