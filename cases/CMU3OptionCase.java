import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Random;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo.OrderSide;
import java.lang.Math;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;

/*
 * Init:
 * 
 */

public class CMU3OptionCase extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();
	private int period;
	private final double R = 0.01;
	private final int MAY_T = 100;
	private final int JUNE_T = 130;
	double[] strikes = {80.0, 90.0, 100.0, 110.0, 120.0};
	private final String UNDERLYING = "CMU3-RAND-E";

	//TODO: Function that returns whether we are in the risk limits with a particular action
	private HashMap<String, Double> bids = new HashMap<String, Double>();
	private HashMap<String, Double> asks= new HashMap<String, Double>();
	private HashMap<String, Double> theo = new HashMap<String, Double>();
	private HashMap<String, Double> delta = new HashMap<String, Double>();
	private HashMap<String, Double> gamma = new HashMap<String, Double>();
	private HashMap<String, Double> vega = new HashMap<String, Double>();
	private HashMap<String, Integer> portfolio = new HashMap<String, Integer>(); //how much of each asset we hold
	private HashMap<String, Integer> currentOrder = new HashMap<String, Integer>();

	private List<String> currentArb = new ArrayList<String>();
	private double UPPER_RISK_LEEWAY = 0.25;
	private double LOWER_RISK_LEEWAY = 0.25;
	private RiskMessage riskLimit;
	private ForecastMessage forecast;
	private double impliedVol = 0.0;
	private boolean DEBUG = false;
	private double PnL = 0.0;
	private double realizedGains = 0.0;
	
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		period = 0;
		log2("starting");

		resetRound();
		riskLimit = new RiskMessage(0.0, 0.0,0.0,0.0,0.0,0.0);
		forecast = null;
		log2("done initializing");
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}

	private void log2(String s) {
		if (DEBUG) {
			log(s);
		}
	}
	

	/** Helper method to zero out currentOrder (called at the start of each round) */
	private void resetRound() {
		Iterator<String> iter = portfolio.keySet().iterator();
		while(iter.hasNext()) {
			String key = (String) iter.next();
			currentOrder.put(key, 0);

		}
		currentArb.clear();
	}

	/** Helper method that brings x within the range [min, max] */
	private double getInRange(double x, double min, double max) {
		if (x > max) return max;
		else if (x < min) return min;
		else return x;
	}
	
	private String maxKeyExcluding(HashMap<String, Double> hm, List<String> excluding) {
		Iterator<String> iter = hm.keySet().iterator();
		Double max = Double.NEGATIVE_INFINITY;
		String result = "";
		String key;
		while (iter.hasNext()) {
			key = iter.next();
			if (excluding.contains(key)) {
				continue;
			}
			if (hm.get(key) > max) {
				max = hm.get(key);
				result = key;
			}
		}
		return result;
	}
	
	private String minKeyExcluding(HashMap<String, Double> hm, List<String> excluding) {
		Iterator<String> iter = hm.keySet().iterator();
		Double min = Double.POSITIVE_INFINITY;
		String result = "";
		String key;
		while (iter.hasNext()) {
			key = iter.next();
			if (excluding.contains(key)) {
				continue;
			}
			if (hm.get(key) < min) {
				min = hm.get(key);
				result = key;
			}
		}
		return result;
	}

	/** Helper method that adds a buy order of amount for an instrument to currentOrder and currentPortfolio */
	private void buy(String instrument, int amount) {

		if (currentOrder.containsKey(instrument)) {
			currentOrder.put(instrument, currentOrder.get(instrument) + amount);
		}
	
	}
	
	/** Calculates risk of portfolio including the currentOrder
	 *  
	 * @return Risk object representing risk of portfolio AND currentOrder
	 */
	private Risk calculatePortfolioRisk() {
		log2("Calculating portfolio Risk...");
		// Delta, gamma, vega
		Risk result = new Risk();
		Iterator<String> iter = portfolio.keySet().iterator();
		String symbol;
		double quantity;
		while(iter.hasNext()) {

			symbol = (String) iter.next();

			quantity = portfolio.get(symbol) + currentOrder.get(symbol);

			result.delta += delta.get(symbol) * quantity;
			result.gamma += gamma.get(symbol) * quantity;
			result.vega += vega.get(symbol) * quantity;

		}
		if (result != null) {
			log2("Done calculating portfolio Risk...");
		}
		return result;
	}

	/** Helper method that calculates theo and greeks using vol and OptionsUtil methods.
	 */
	private void updateTheoAndGreeks() {
		double S = 100.0;
		if (bids.containsKey(UNDERLYING) && asks.containsKey(UNDERLYING)) {
			S = (bids.get(UNDERLYING) + asks.get(UNDERLYING))/2.0;
		}
		log2("Stock trading at : " + S);

		double timeToMaturity;
		double K;
		String instrument;
		InstrumentDetails details;
		Iterator<String> iter = portfolio.keySet().iterator();
		while (iter.hasNext()) {
			instrument = iter.next();
			details = instruments().getInstrumentDetails(instrument);
			K = details.strikePrice;
			timeToMaturity = (instrument.contains("0527") ? (double) (MAY_T - period) : (double) (JUNE_T - period))/365.0; // TODO: UNHACK THIS

			theo.put(instrument, Optionsutil.Call(S, K, timeToMaturity, R, impliedVol));
			delta.put(instrument, Optionsutil.calculateDelta(S, K, timeToMaturity, R, impliedVol));
			gamma.put(instrument, Optionsutil.calculateGamma(S, K, timeToMaturity, R, impliedVol));
			vega.put(instrument, Optionsutil.calculateVega(S, K, timeToMaturity, R, impliedVol));

		}

		delta.put(UNDERLYING, 1.0);
		gamma.put(UNDERLYING, 0.0);
		vega.put(UNDERLYING, 0.0);
		log2("Done with updateTheo");
	}


	/**
	 * Two step helper method that:
	 * 		1. Determines the amount each asset is undervalued (if it is)
	 * 		2. Modifies currentOrder to exploit arbitrage opportunity for each asset
	 */
	private void determineArb() {
		// Asset maps to amount that it is undervalued (negative if overvalued)
		HashMap<String, Double> amountUndervalued = new HashMap<String, Double>();
		String buyArb = "";
		String sellArb = "";
		double maxBuy = 0.0;
		double maxSell = 0.0;
		Iterator<String> iter = portfolio.keySet().iterator();
		String symbol;
		while(iter.hasNext()) {
			symbol = (String) iter.next();
			// Undervalued
			if (asks.containsKey(symbol) && (theo.get(symbol) > asks.get(symbol))) {
				if ((theo.get(symbol) - asks.get(symbol)) > maxBuy) {
					maxBuy = theo.get(symbol) - asks.get(symbol);
					buyArb = symbol;
				}
			}
			// Overvalued
			else if (bids.containsKey(symbol) && (theo.get(symbol) < bids.get(symbol))) {
				if ((bids.get(symbol) - theo.get(symbol)) > maxSell) {
					maxSell = bids.get(symbol) - theo.get(symbol);
					sellArb = symbol;
				}
			}
		}
		
		// Here buyArb is the most undervalued symbol and sellArb is the most overvalued. Exploit arb
		
		Random rand = new Random();
		if (rand.nextInt(2) == 0) {
			exploitArbitrage(buyArb, OrderSide.BUY);
			exploitArbitrage(sellArb, OrderSide.SELL);
		}
		else {
			exploitArbitrage(sellArb, OrderSide.SELL);
			exploitArbitrage(buyArb, OrderSide.BUY);
		}
	}
	
	private Risk riskDiff(String s1, String s2) {
		double d = delta.get(s1) - delta.get(s2);
		double g = gamma.get(s1) - gamma.get(s2);
		double v = vega.get(s1) - vega.get(s2);
		return new Risk(d, g, v);

	}

	private int calculateArbOrder(Risk upperLeeway, Risk lowerLeeway, Risk strat) {
		Risk maxOrder = new Risk();
		maxOrder.delta = strat.delta > 0.0 ? upperLeeway.delta/strat.delta : lowerLeeway.delta/Math.abs(strat.delta);
		maxOrder.gamma = strat.gamma > 0.0 ? upperLeeway.gamma/strat.gamma : lowerLeeway.gamma/Math.abs(strat.gamma);
		maxOrder.vega = strat.vega > 0.0 ? upperLeeway.vega/strat.vega : lowerLeeway.vega/Math.abs(strat.vega);
		return (int) Math.round(Math.floor(Math.min(Math.min(maxOrder.delta, maxOrder.gamma), maxOrder.vega)));
	}
	
	/**
	 * Exploit arbitrage of type (BUY or SELL) for option with hedging
	 */
	private void exploitArbitrage(String option, OrderSide type) {
		if (option == "") return; //No arbitrage possible
		String optionHedge;
		if (option.contains("0527")) {
			optionHedge = option.replace("0527", "0627");
		}
		else {
			optionHedge = option.replace("0627", "0527");
		}
		Risk pRisk = calculatePortfolioRisk();
		Risk upperLeeway = new Risk(riskLimit.maxDelta - pRisk.delta, riskLimit.maxGamma - pRisk.gamma, riskLimit.maxVega - pRisk.vega);
		Risk lowerLeeway = new Risk(pRisk.delta - riskLimit.minDelta, pRisk.gamma - riskLimit.minGamma, pRisk.vega - riskLimit.minVega);

		if (type.equals(OrderSide.BUY)) {
			Risk strat = riskDiff(option, optionHedge);
			
			int longStratOrderSize = calculateArbOrder(upperLeeway, lowerLeeway, strat);
			buy(option, longStratOrderSize);
			buy(optionHedge, -longStratOrderSize);
		}
		else {
			Risk strat = riskDiff(optionHedge, option);

			int shortStratOrderSize = calculateArbOrder(upperLeeway, lowerLeeway, strat);
			buy(optionHedge, shortStratOrderSize);
			buy(option, -shortStratOrderSize);	
			
		}
		
		currentArb.add(option);
		currentArb.add(optionHedge);
	}
	
	/**
	 * Buys/shorts assets to balance gamma and vega
	 * Ensures: gamma and vega of portfolio + currentOrder is under risk
	 */
	private void balanceGamma() {
		String balOption = maxKeyExcluding(gamma, currentArb);
		Risk pRisk = calculatePortfolioRisk();
		double gammaDeviation = forecast.gamma - pRisk.gamma;
		double deltaLeeway, vegaLeeway, orderSize;
		if (gammaDeviation > 0.0) {
			deltaLeeway = riskLimit.maxDelta - pRisk.delta;
			vegaLeeway = riskLimit.maxVega - pRisk.vega;
			orderSize = Math.min(Math.min(deltaLeeway/delta.get(balOption), vegaLeeway/vega.get(balOption)), gammaDeviation/gamma.get(balOption));
			buy(balOption, (int) Math.round(orderSize));
		}
		
		else {
			deltaLeeway = pRisk.delta - riskLimit.minDelta;
			vegaLeeway = pRisk.vega - riskLimit.minVega;
			orderSize = Math.min(Math.min(deltaLeeway/delta.get(balOption), vegaLeeway/vega.get(balOption)), gammaDeviation/gamma.get(balOption));
			buy(balOption, (int) -Math.round(orderSize));
		}

	}
	
	private void balanceVega() {
		String balOption = maxKeyExcluding(vega, currentArb);
		Risk pRisk = calculatePortfolioRisk();
		double vegaDeviation = forecast.vega - pRisk.vega;
		double deltaLeeway, gammaLeeway, orderSize;
		if (vegaDeviation > 0.0) {
			deltaLeeway = riskLimit.maxDelta - pRisk.delta;
			gammaLeeway = riskLimit.maxGamma - pRisk.gamma;
			orderSize = Math.min(Math.min(deltaLeeway/delta.get(balOption), vegaDeviation/vega.get(balOption)), gammaLeeway/gamma.get(balOption));
			buy(balOption, (int) Math.round(orderSize));
		}
		
		else {
			deltaLeeway = pRisk.delta - riskLimit.minDelta;
			gammaLeeway = pRisk.gamma - riskLimit.minGamma;
			orderSize = Math.min(Math.min(deltaLeeway/delta.get(balOption), vegaDeviation/vega.get(balOption)), gammaLeeway/gamma.get(balOption));
			buy(balOption, (int) -Math.round(orderSize));

		}


	}
	
	/**
	 * Buys/shorts assets to balance delta
	 * TODO: Check that this is intelligent (does this affect gamma/vega?)
	 * 
	 * Ensures delta of portfolio + currentOrder is within risk
	 */
	private void balanceDelta() {
		Risk risk = calculatePortfolioRisk();
		// Buy the delta difference in stock to match forecast
		buy(UNDERLYING, (int) Math.round(forecast.delta - risk.delta));
	}

	private void updatePnL() {
		PnL = realizedGains;
		Iterator<String> iter = portfolio.keySet().iterator();
		String symbol;
		int quantityHeld;
		while(iter.hasNext()) {
			symbol = iter.next();
			quantityHeld = portfolio.get(symbol);
			// We are long, look at bid price
			if (quantityHeld > 0) {
				PnL += quantityHeld * bids.get(symbol);
			}
			else if (quantityHeld < 0) {
				PnL += quantityHeld * asks.get(symbol);
			}
		}
	}
	
	/** Receives and stores a new bid and ask for a particular option/the underlying */
	public void newBidAsk(String idSymbol, double bid, double ask) {
		knownSymbols.add(idSymbol);
		bids.put(idSymbol, bid);
		asks.put(idSymbol, ask);
		
		// New symbol, add it to the portfolio
		if (!portfolio.containsKey(idSymbol)) {
			portfolio.put(idSymbol, 0);
			currentOrder.put(idSymbol, 0);
		}

		log2("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}

	/** Logs when the exchange fulfills an order, for debugging purposes mainly */
	public void orderFilled(int volume, double fillPrice) {
		log2("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	/** Receives and stores a risk message with leeway */
	public void newRiskMessage(RiskMessage msg) {
		double deltaDiff = msg.maxDelta - msg.minDelta;
		double gammaDiff = msg.maxGamma - msg.minGamma;
		double vegaDiff = msg.maxVega - msg.minVega;
		
		double minDelta = msg.minDelta + LOWER_RISK_LEEWAY*deltaDiff;
		double minGamma = msg.minGamma + LOWER_RISK_LEEWAY*gammaDiff;
		double minVega = msg.minVega +  LOWER_RISK_LEEWAY*vegaDiff;
		double maxDelta = msg.maxDelta - UPPER_RISK_LEEWAY*deltaDiff;
		double maxGamma = msg.maxGamma - UPPER_RISK_LEEWAY*gammaDiff;
		double maxVega = msg.maxVega - UPPER_RISK_LEEWAY*vegaDiff;
		riskLimit = new RiskMessage(minDelta, maxDelta, minGamma, maxGamma, minVega, maxVega);
		log2("I received a risk message!");
	}

	/** Handles a new forecast message. Brings forecast within risk limits and stores it
	 */
	public void newForecastMessage(ForecastMessage msg) {
		// Bring forecast within risk limit
		forecast = msg;
		if (riskLimit != null) {
			double delta = getInRange(msg.delta, riskLimit.minDelta, riskLimit.maxDelta);
			double gamma = getInRange(msg.gamma, riskLimit.minGamma, riskLimit.maxGamma);
			double vega = getInRange(msg.vega, riskLimit.minVega, riskLimit.maxVega);
			forecast = new ForecastMessage(delta, gamma, vega);
		}
		log2("I received a forecast message!");
	}
	
	/** Main logic of algorithm occurs here, once per period
	 *  Stores the new vol, zeroes out the current order, 
	 *  and updates theo/greeks for all options
	 *  
	 *  TODO: Determine whether this is the final method called in each round before placeOrders
	 */
	public void newVolUpdate(VolUpdate msg) {
		log2("entering volupdate");
		impliedVol = msg.impliedVol;
		
		log2("I received a vol update message!");
	}
	


	/** Called at the end of each round to collect orders
	 *  Basically translates currentOrder into an OrderInfo[]
	 */
	public OrderInfo[] placeOrders() {
		resetRound(); // clears the order map for this period
		if (PnL > 0.0 || PnL < -20000.0) {
			return new OrderInfo[0];
		}
		log2("reset order in volupdate");
		updateTheoAndGreeks(); //update theo based on today's vol
		log2("updated theo in volupdate");

		determineArb(); //check for arbitrage and act on it if possible
		log2("determined arb in volupdate");

		// Avoid bias with randomness and iteration
		Random rand = new Random();
		for (int i = 0; i < 2; i++) {	
			if (rand.nextInt(2) == 0) {
				balanceGamma(); 
				balanceVega();
			}
			else {
				balanceVega();
				balanceGamma();
			}
		}
		log2("balanced gamma/vega in volupdate");
		log2("printing orders");
		printOrder();
		balanceDelta(); //balance delta
		log2("balanced delta");
		// Wait for orders to be filled... 
		Iterator<String> iter = currentOrder.keySet().iterator();
		ArrayList<OrderInfo> orders = new ArrayList<OrderInfo>();
		String symbol;
		int quantity;


		while (iter.hasNext()) {
			symbol = (String) iter.next();
			log2("now ordering " + symbol);
			quantity = currentOrder.get(symbol);
			if (quantity > 0 && asks.containsKey(symbol)) {
				log2("trying to buy + " + symbol + " at " + asks.get(symbol));
				realizedGains -= quantity*asks.get(symbol);
				orders.add(new OrderInfo(symbol, OrderSide.BUY, asks.get(symbol), quantity));
			}
			else if (quantity < 0 && bids.containsKey(symbol)) {
				log2("trying to sell + " + symbol + " at " + bids.get(symbol));
				realizedGains += quantity*bids.get(symbol);
				orders.add(new OrderInfo(symbol, OrderSide.SELL, bids.get(symbol), -quantity));
			}
		}

		updatePnL();
		log("At the end of period " + period + "our PnL is " + PnL);

		OrderInfo[] result = new OrderInfo[orders.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = orders.get(i);
		}
		period++;
		return result;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		// Update the portfolio with this order
		portfolio.put(idSymbol, portfolio.get(idSymbol) + quantity);
		log2("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		portfolio.put(idSymbol, portfolio.get(idSymbol) + quantity);
		log2("Penalty called...oh no!");
	}

	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

	/* DEBUGGING METHODS */
	void printOrder() {
		Iterator<String> iter = currentOrder.keySet().iterator();
		String instrument;
		while (iter.hasNext()) {
			instrument = (String) iter.next();
			log2("Ordering " + currentOrder.get(instrument) + " many of " + instrument);
		}
		return;
	}


	private class Risk {
		double delta;
		double gamma;
		double vega;

		Risk() {
			this.delta = 0.0;
			this.gamma = 0.0;
			this.vega = 0.0;
		}
		Risk(double d, double g, double v) {
			this.delta = d;
			this.gamma = g;
			this.vega = v;		
		}
	}

}
