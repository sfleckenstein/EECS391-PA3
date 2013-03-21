import edu.cwru.sepia.environment.model.state.ResourceType;

public class GotoResource2 extends Act{
	private int moverID1;
	private int moverID2;
	private ResourceType resource;
	
	public GotoResource2(int moverID1, int moverID2, ResourceType resource) {
		this.moverID1 = moverID1;
		this.moverID2 = moverID2;
		this.resource = resource;
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
	
	public ResourceType getResource() {
		return resource;
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
