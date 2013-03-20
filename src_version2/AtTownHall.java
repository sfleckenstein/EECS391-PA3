public class AtTownHall extends Literal {
	
	private int objectID;
	
	public AtTownHall(int objectID) {
		this.objectID = objectID;
	}
	
	public int getObjectID() {
		return objectID;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class AtTownHall")) {
			return false;
		}
		AtTownHall a = (AtTownHall)o;
		if(a.objectID == this.objectID) {
			return true;
		}
		return false;
	}
}
