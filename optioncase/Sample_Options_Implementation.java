import java.util.ArrayList;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.options.OptionSignals.AdminMessage;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.options.OrderInfo;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class Sample_Options_Implementation extends AbstractOptionsCase {
	
	//This sample implementation abstracts all interface interaction to the class below
	class OptionsBook{
		//Useful class to maintain information on option markets, forecasts, risk limits, and your private options position
		private List<String> Instrument_Names = new ArrayList<String>();
		private List<double[]> BidAsk = new ArrayList<double[]>();
		private List<Integer> QuantityHeld = new ArrayList<Integer>();
		private List<Integer> Forecast = new ArrayList<Integer>();
		private List<Integer> Risk = new ArrayList<Integer>();
		
		public OptionsBook() {
			//Constructor
			//Initiate Instrument_Names elements to the correct id_symbols
			BidAsk.clear();
			QuantityHeld.clear();
		}
		
		public void Save_BidAsk(String idSymbol, double bid, double ask) {
		// Store new bid/ask information into BidAsk array
		int index = Instrument_Names.indexOf(idSymbol);
		double[] Bid_Ask = new double[2];
		Bid_Ask[0] = bid;
		Bid_Ask[1] = ask;
		BidAsk.remove(index);
		BidAsk.add(index, Bid_Ask);
		}
		
		public void Save_OrderFill(String idSymbol, int volume, double fillPrice){
			//Updates your position
			int index = Instrument_Names.indexOf(idSymbol);
			int prior_quantity = QuantityHeld.get(index);
			QuantityHeld.remove(index);
			QuantityHeld.add(index,volume+prior_quantity);
		}
		
		//Other important functions 
		public void UpdateForecast(){}
		
		public void UpdateRisk() {}
		
		public double getPnL() {}
		
		public double[] getGreeks() {}
		
		//For your implementation, you may want to break this down into several functions as the logic may be complicated
		public OrderInfo[] TradingAlgo(){
			OrderInfo[] orders = new OrderInfo[Instrument_Names.size()];	
			//if the underlying is greater than 100, buy everything!
			if (BidAsk.get(0)[0] > 100.0) {
				for (int i = 0; i < Instrument_Names.size(); i++) {
					orders[i] = new OrderInfo(Instrument_Names.get(i), OrderSide.BUY, 100.00, 10);
				}
			}
			return orders;
		}
		
	}
	
	class MySampleOptionImplementation implements OptionsCase {	
		private IDB myDatabase;
		int factor;
		private OptionsBook Book = new OptionsBook();

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system. This this optional.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "8897");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds. Again, optional.
			myDatabase = database;
			factor = getIntVar("someFactor"); // helper method for accessing declared variables
		}

		public void newBidAsk(String idSymbol, double bid, double ask) {
			log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);		
			//Store bid ask info into our options book
			Book.Save_BidAsk(idSymbol, bid, ask);
		}
					
		public void orderFilled(String idSymbol, int volume, double fillPrice) {
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			Book.Save_OrderFill(idSymbol, volume, fillPrice);
		}


		public void newAdminMessage(AdminMessage msg) {
			log("I received an admin message!");
			Book.UpdateRisk();
		}


		public void newForecastMessage(ForecastMessage msg) {
			log("I received a forecast message!");
			Book.UpdateForecast();
		}

		public OrderInfo[] placeOrders() {
			OrderInfo[] orders = new OrderInfo[Book.Instrument_Names.size()];
			orders = Book.TradingAlgo();
			return orders;
		}	
	}

	public OptionsCase getOptionCaseImplementation() {
		return new MySampleOptionImplementation();
	}

}