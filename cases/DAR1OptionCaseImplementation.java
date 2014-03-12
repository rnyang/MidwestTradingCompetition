

import java.util.ArrayList;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


public class DAR1OptionCaseImplementation extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();

	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		knownSymbols.add(idSymbol);
		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}

	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		log("I received an admin message!");
	}

	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");
	}
	
	public void newVolUpdate(VolUpdate msg) {
		log("I received a vol update message!");
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		log("Placing orders");
		OrderInfo[] orders = new OrderInfo[0];
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
