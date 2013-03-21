import edu.cwru.sepia.environment.model.state.ResourceType;

public class GotoResource extends Act {
	
	private int moverID;
	private ResourceType resource;
	
	
	public GotoResource(int moverID, ResourceType resource) {
		this.moverID = moverID;
		this.resource = resource;
	}

	public int getMoverID() {
		return moverID;
	}
	
	public ResourceType getResource() {
		return resource;
	}
	
	public String getResourceString() {
		String str;
		switch(resource) {
		case GOLD:
			str = "GOLD MINE";
			break;
		case WOOD:
			str = "FOREST";
			break;
		default:
			str = "";
		}
		return str;
	}

}
