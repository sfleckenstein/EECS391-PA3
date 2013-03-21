public class GotoTownHall2 extends Act {
	
	private int moverID1;
	private int moverID2;
	
	public GotoTownHall2(int moverID1, int moverID2) {
		this.moverID1 = moverID1;
		this.moverID2 = moverID2;
	}
	
	public int getMoverID(int idNum) {
		switch(idNum) {
		case 1:
			return moverID1;
		case 2:
			return moverID2;
		}
		return -1;
	}
	
	
}
