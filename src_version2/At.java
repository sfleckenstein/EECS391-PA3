import java.awt.Point;


public class At extends Literal {

	private int objectID;
	private Point position;
	
	public At(int objectID, Point position) {
		this.objectID = objectID;
		this.position = position;
	}
	
	public int getObjectID() {
		return objectID;
	}
	
	public Point getPosition() {
		return position;
	}
	
	@Override
	public boolean equals(Object o) { //must make sure the object is of class At before using
		At a = (At)o;
		if(a.objectID == this.objectID && a.position.equals(this.position))
			return true;
		return false;
	}
	
}
