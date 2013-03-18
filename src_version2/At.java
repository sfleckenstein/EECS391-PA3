import java.awt.Point;


public class At extends Literal {

	private int objectID;
	private Point position;
	
	/**
	 * 
	 * @param objectID - ID of the object
	 * @param position - Position the object is at
	 */
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
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class At")) {
			return false;
		}
		At a = (At)o;
		if(a.objectID == this.objectID && a.position.equals(this.position)) {
			return true;
		}
		return false;
	}
	
}
