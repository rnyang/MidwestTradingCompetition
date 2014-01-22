import com.optionscity.freeway.api.messages.Signal;


public class SampleSignal extends Signal {
	
	public final String symbol;
	public final double price;
	public final int quantity;
	
	public SampleSignal(String signalString) {
		super(SampleSignal.class.getSimpleName());
		String[] parts = signalString.split(",");
		symbol = parts[0];
		price = Double.parseDouble(parts[1]);
		quantity = Integer.parseInt(parts[2]);
	}

}
