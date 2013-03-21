public class GotoTownHall3 extends Act {
	
	private int moverID1;	
	private int moverID2;
	private int moverID3;
	
	public GotoTownHall3(int moverID1, int moverID2, int moverID3) {
		this.moverID1 = moverID1;
		this.moverID2 = moverID2;
		this.moverID3 = moverID3;
	}
	
	public int getMoverID(int idNum) {
		switch(idNum) {
		case 1:
			return moverID1;
		case 2:
			return moverID2;
		case 3:
			return moverID3;
		}
		return -1;
	}
}