import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.Instrument;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IGrid;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;
import com.optionscity.freeway.api.Prices;


public class SurfaceVisualizer extends AbstractOptionsCase implements OptionsCase {
	
	private static final String VOL_GRID = "VOL";
	private static final String GAMMA_GRID = "GAMMA";
	private IGrid volGrid;
	private IGrid gammaGrid;
	private Map<String, double[]> priceMap = new HashMap<String, double[]>();
	private double underlyingPrice;
	
	public void addVariables(IJobSetup setup) {}

	public void initializeAlgo(IDB dataBase) {}

	public void newRiskMessage(RiskMessage msg) {}

	public void newForecastMessage(ForecastMessage msg) {}

	public void newVolUpdate(VolUpdate msg) {}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		InstrumentDetails details = new InstrumentDetails();
		if (!details.type.isOption()) {
			underlyingPrice = (bid + ask) / 2;
		}
		else {
			priceMap.put(idSymbol, new double[] {bid, ask});
		}
	}

	public OrderInfo[] placeOrders() {
		return new OrderInfo[] {};
	}

	public void orderFilled(String idSymbol, double price, int quantity) {}

	public void penaltyFill(String idSymbol, double price, int quantity) {}

	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
