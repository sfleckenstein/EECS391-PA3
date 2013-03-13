
public class Has extends Literal {
	
	private int holderID;
	private int toHoldID;
	
	public Has(int holderID, int toHoldID) {
		this.holderID = holderID;
		this.toHoldID = toHoldID;
	}

	public int getHolderID() {
		return holderID;
	}
	
	public int getToHold() {
		return toHoldID;
	}

	@Override
	public boolean equals(Object o) { //must make sure the object is of class Has before using
		Has h = (Has)o;
		if(h.holderID == this.holderID && h.toHoldID == this.toHoldID)
			return true;
		return false;
	}
}
