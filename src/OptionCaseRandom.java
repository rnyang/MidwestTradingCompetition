import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.Prices;


public class OptionCaseRandom extends AbstractOptionsCase implements OptionsCase {
	
	List<String> symbols = new ArrayList<String>();
	Random r = new Random();
	int events = 0;

	public void addVariables(IJobSetup setup) {}

	public void initializeAlgo(IDB dataBase) {}

	public void newRiskMessage(RiskMessage msg) {}

	public void newForecastMessage(ForecastMessage msg) {}

	public void newVolUpdate(VolUpdate msg) {}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		log("Received market data");
		symbols.add(idSymbol);
	}

	public OrderInfo[] placeOrders() {
		int products = r.nextInt(4);
		if (!symbols.isEmpty()) {
			OrderInfo[] orders = new OrderInfo[products];
			for (int i = 0; i < products; i++) {
				boolean approved = r.nextBoolean();
				String product = symbols.get(r.nextInt(symbols.size()));
				int qty = r.nextInt(5);
				Prices prices = instruments().getAllPrices(product);
				boolean sell = r.nextBoolean();
				OrderSide side = (sell) ? OrderSide.SELL : OrderSide.BUY;
				orders[i] = new OrderInfo(product, side, (sell) ? prices.bid : prices.ask, qty);
			}
			return orders;
		}
		return new OrderInfo[] {};
	}

	public void orderFilled(String idSymbol, double price, int quantity) {}

	public void penaltyFill(String idSymbol, double price, int quantity) {}

	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
