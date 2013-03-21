import edu.cwru.sepia.environment.model.state.ResourceType;

public class GotoResource3 extends Act{
	private int moverID1;
	private int moverID2;
	private int moverID3;
	private ResourceType resource1;
	private ResourceType resource2;	
	private ResourceType resource3;
	
	public GotoResource3(int moverID1, ResourceType resource1,
			int moverID2, ResourceType resource2,
			int moverID3, ResourceType resource3) {
		this.moverID1 = moverID1;
		this.moverID2 = moverID2;
		this.moverID3 = moverID3;
		this.resource1 = resource1;
		this.resource2 = resource2;
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
	
	public ResourceType getResource(int idNum) {
		switch(idNum) {
		case 1:
			return resource1;
		case 2:
			return resource2;
		case 3:
			return resource3;
		}
		return null;
	}
	

//	public String getResourceString() {
//		String str;
//		switch(resource) {
//		case GOLD:
//			str = "GOLD MINE";
//			break;
//		case WOOD:
//			str = "FOREST";
//			break;
//		default:
//			str = "";
//		}
//		return str;
//	}

}