public class ContainsPeasants extends Literal {

	private int numPeasants;
	
	public ContainsPeasants(int numPeasants) {
		this.numPeasants = numPeasants;
	}
	
	public int getNumPeasants() {
		return numPeasants;
	}
	
	public void setNumPeasants(int numPeasants) {
		this.numPeasants = numPeasants;
	}

	@Override
	public boolean equals(Object o) {
		if(!o.getClass().toString().equals("class ContainsPeasants")) {
			return false;
		}
		ContainsPeasants cp = (ContainsPeasants)o;
		if(cp.numPeasants == this.numPeasants) {
			return true;
		}
		return false;
	}	
}
