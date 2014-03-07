import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class SimpleCase extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();
	private Portfolio positions = new Portfolio();
	boolean killJob = false;
	
	public void addVariables(IJobSetup setup) {}

	public void initializeAlgo(IDB database) {
		myDatabase = database; 
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		if (killJob)
			container.stopJob("Check Logs");
		knownSymbols.add(idSymbol);
	}

	public void newRiskMessage(RiskMessage msg) {
		if (killJob)
			container.stopJob("Check Logs");
		//log("I received an admin message!");
	}

	public void newForecastMessage(ForecastMessage msg) {
		if (killJob)
			container.stopJob("Check Logs");
		//log("I received a forecast message!");
	}
	
	public void newVolUpdate(VolUpdate msg) {
		if (killJob)
			container.stopJob("Check Logs");
		
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
		PortfolioRisk risk = calculateRisk();
		log("post-liquidation risk is, gamma=" + risk.gamma + ", vega=" + risk.vega + ", delta=" + risk.delta);
	
		if (killJob)
			container.stopJob("Check Logs");
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		if (killJob)
			container.stopJob("Check Logs");
		OrderInfo[] orders = new OrderInfo[] {};
		if (knownSymbols.isEmpty()) {
			log("I don't know of any symbols yet.  Returning empty array.");
		}
		else {
			orders = new OrderInfo[1];
			String symbol = knownSymbols.get(1);
			orders[0] = new OrderInfo(symbol, OrderSide.BUY, 1000, 2);
		}
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order was filled with qty of " + quantity + " at a price of " + price);
		PortfolioRisk risk = calculateRisk();
		log("Current risk is");
		log("  vega=" + risk.vega);
		log("  gamma=" + risk.gamma);
		log("  delta=" + risk.delta);
		killJob = false;
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}
	
	class Portfolio {
		private HashMap<String, double[]> positions; 
		private HashMap<String, String[]> types; 
		private HashMap<String, double[]> pricesNtime; 
		private double vol;
		private double r = 0.1;
		
		//values [bid,ask, amount]
		
		public Portfolio(){

		}
		
		public void AddPosition(String ticker, double[] values){
			//subject to change
			
		}
		
		public void SetVol(double volatility){vol = volatility;}
		
		//values = [bid, ask, time]
		public void UpdatePrice(String ticker, double[] values){
			pricesNtime.put(ticker, values);
			
		}
		
		public double Delta(String ticker){
			double spot = (pricesNtime.get(ticker)[0] + pricesNtime.get(ticker)[1])/2;
			double strike = Double.valueOf(types.get(ticker)[1]);
			double time = pricesNtime.get(ticker)[2];
			double delta = Optionsutil.calculateDelta(spot, strike, time, r, vol);
			return delta; 
		}
		
		public double Gamma(String ticker){
			double spot = (pricesNtime.get(ticker)[0] + pricesNtime.get(ticker)[1])/2;
			double strike = Double.valueOf(types.get(ticker)[1]);
			double time = pricesNtime.get(ticker)[2];
			double gamma = Optionsutil.calculateGamma(spot, strike, time, r, vol);
			return gamma; 
		}
		
		public double Vega(String ticker){
			double spot = (pricesNtime.get(ticker)[0] + pricesNtime.get(ticker)[1])/2;
			double strike = Double.valueOf(types.get(ticker)[1]);
			double time = pricesNtime.get(ticker)[2];
			double vega = Optionsutil.calculateVega(spot, strike, time, r, vol);
			return vega; 
		}
		
		public double PortfolioDelta(){
			double pdelta = 0;
			for(String key: positions.keySet()){
				if(types.get(key)[0].equals("Option"))
					pdelta += this.Delta(key) * positions.get(key)[2];
				else if(types.get(key).equals("Stock"))
					pdelta += positions.get(key)[2];
			}
			return pdelta; 
		}
		
		public double PortfolioGamma(){
			double pgamma = 0; 
			for(String key: positions.keySet()){
				if(types.get(key)[0].equals("Option"))
					pgamma += this.Gamma(key) * positions.get(key)[2];
			}
			return pgamma; 
		}
		
		public double PortfolioVega(){
			double pvega = 0;
			for(String key: positions.keySet()){
				if(types.get(key)[0].equals("Option"))
					pvega += this.Vega(key) * positions.get(key)[2];
			}
			return pvega; 
		}
		
		public int HedgeDelta(){return (int) -this.PortfolioDelta();}
	}

}