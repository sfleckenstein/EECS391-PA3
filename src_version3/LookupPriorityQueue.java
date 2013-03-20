import java.util.PriorityQueue;

public class LookupPriorityQueue<E> extends PriorityQueue<E> {

	private static final long serialVersionUID = 1L;

	public E get(E n) {
		for(Object node : this) {
			if(((E) node).equals(n)) {
				return (E)node;
			}
		}
		return null;
	}
}
