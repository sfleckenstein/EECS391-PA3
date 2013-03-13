import java.util.ArrayList;

import edu.cwru.sepia.environment.model.state.State.StateView;


public class Node implements Comparable<Object>{
	
	private StateView state;
	private Node parent;
	private Act toState;
	private ArrayList<Literal> stateLits;
	private int costToNode;
	private int costToGoal;

	/**
	 * 
	 * @param state - The StateView of the node
	 * @param parent - Parent node
	 * @param toState - The Act to get to this node
	 * @param stateLits - The list of closed world assumptions
	 * @param costToNode - The total cost to get to this node
	 * @param costToGoal - Estimated cost to goal
	 */
	public Node(StateView state, Node parent, Act toState, ArrayList<Literal> stateLits, int costToNode, int costToGoal) {
		this.state = state;
		this.parent = parent;
		this.toState = toState;
		this.stateLits = stateLits;
		this.costToNode = costToNode;
		this.costToGoal = costToGoal;
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
	
	public int getCostToGoal() {
		return costToGoal;
	}
	
	public Act getToState() {
		return toState;
	}
	
	public ArrayList<Literal> getStateLits() {
		return stateLits;
	}
	
	//TODO make a method that can be used to see if a specific literal is in the node
	//I sorta started this method for you. You can use the equals methods in At and Has to compare them
	//but just make sure that you use the correct class
	public boolean containsLit(Literal toFind) {
		Class<? extends Literal> findClassType = toFind.getClass();
		for(Literal l : stateLits) {
			Class<? extends Literal> classType = l.getClass();
			if(findClassType.equals(classType)) {
				//for has class
				if(findClassType.equals(Class<Has>)) {
					if(toFind.equals(l)) return true;
				}
				//for at class
				if(findClassType.equals(Class<At>))){
					if toFind.equals(l)) return true;
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

}
