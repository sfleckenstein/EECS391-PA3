import java.util.ArrayList;


public class Node {
	
	private Node parent;
	private ArrayList<Node> children = new ArrayList<Node>();
	private Act toState;
	//TODO figure out how to represent literals
	//possibly another abstract class
	//private ArrayList<Literal> stateLits; 
	
	public Node(Node parent, ArrayList<Node> children, Act toState) {
		this.parent = parent;
		this.children = children;
		this.toState = toState;
	}

}
