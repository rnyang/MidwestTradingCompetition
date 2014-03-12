import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.lang.Math;

import com.optionscity.freeway.api.InstrumentDetails;
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

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class HAR1OptionCaseImplementation extends AbstractOptionsCase implements OptionsCase {
	
	class SolutionObject {
		double bestPrice;
		int best_c1;
		int best_c2;
		int best_underlyingAmount;
		String best_p1;
		String best_p2;

		public SolutionObject(double bp, int bc1, int bc2, int bu, String bp1, String bp2) {
			bestPrice = bp;
			best_c1 = bc1;
			best_c2 = bc2;
			best_underlyingAmount = bu;
			best_p1 = bp1;
			best_p2 = bp2;
		}
	}
	
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();

    // parameters
    private final double RATE = 0.01;
    private double time = -1.0;

    private double forecastDelta;
    private double forecastGamma;
    private double forecastVega;

    private double minDelta;
    private double maxDelta;
    private double minGamma;
    private double maxGamma;
    private double minVega;
    private double maxVega;
    private double impliedVol;
    private double money = 0;
    private double pnl;
    private double deltapos;
    String temp;
    
    private String underlyingSymbol;

    private HashMap<String, Double> bids = new HashMap<String, Double>();
    private HashMap<String, Double> asks = new HashMap<String, Double>();
    private HashMap<String, Integer> positions = new HashMap<String, Integer>();
    // end parameters

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
		if (knownSymbols.size() < 11) {
            knownSymbols.add(idSymbol);
        }
		if (!positions.containsKey(idSymbol)) {
			positions.put(idSymbol, 0);
		}
        bids.put(idSymbol, bid);
        asks.put(idSymbol, ask);

		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}

	/*public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}*/

	public void newRiskMessage(RiskMessage msg) {
        minDelta = msg.minDelta;
        maxDelta = msg.maxDelta;
        minGamma = msg.minGamma;
        maxGamma = msg.maxGamma;
        minVega = msg.minVega;
        maxVega = msg.maxVega;
		log("I received an admin message!");
	}

	public void newForecastMessage(ForecastMessage msg) {
        forecastDelta = msg.delta;
        forecastGamma = msg.gamma;
        forecastVega = msg.vega;
	}
	
	public void newVolUpdate(VolUpdate msg) {
        impliedVol = msg.impliedVol;
        time = time + 1;
        if (time == 101) {
        	time = 0;
        }
		log("I received a vol update message!");
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
        // update our positions
        if (positions.containsKey(idSymbol)) {
            positions.put(idSymbol, positions.get(idSymbol) + quantity);
        }
        else {
            // shouldn't really get here..everything that we get a penalty for should be in positions already
            log("something weird is going on: penaltyFill");
            positions.put(idSymbol, quantity);
        }
		log("Penalty called...oh no!");
	}

    // method for valuation and finding optimal combination
    private SolutionObject findOptimalCombination() {
        HashMap<String, Double> deltas = new HashMap<String, Double>();
        HashMap<String, Double> gammas = new HashMap<String, Double>();
        HashMap<String, Double> vegas = new HashMap<String, Double>();
        HashMap<String, Double> prices = new HashMap<String, Double>();
        log("test");
        if (time < 1) {
        	for (String sym: knownSymbols) {
        		InstrumentDetails details = instruments().getInstrumentDetails(sym);
        		if (!details.type.isOption()) {
        			underlyingSymbol = sym;
        		}			
        	}
        }

        for (String sym: knownSymbols) {
            InstrumentDetails details = instruments().getInstrumentDetails(sym);
            if (details.type.isOption()){
                //note: here we use midpoint of the spot. note convexity of the greeks/price functions(?)
                double spot = (bids.get(underlyingSymbol) + asks.get(underlyingSymbol)) / 2.0;
                double timeToExpiry = (100.0-time)/365.0;
                Double delta = Optionsutil.calculateDelta(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Underlying Delta:" + delta);
                Double gamma = Optionsutil.calculateGamma(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Gamma: " + gamma);
                Double vega = Optionsutil.calculateVega(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Vega: " + vega);	
                Double price = Optionsutil.Call(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                deltas.put(sym, delta);
                gammas.put(sym, gamma);
                vegas.put(sym, vega);
                prices.put(sym, price);
            }
            // also put in underlying as a 1 delta, 0 vega, 0 gamma instrument
            else {
            	underlyingSymbol = sym;
                deltas.put(sym, 1.0);
                gammas.put(sym, 0.0);
                vegas.put(sym, 0.0);
                prices.put(sym, (bids.get(sym)+asks.get(sym)) / 2.0);
            }
        }
		
		// calculate current net positions
		double current_delta = 0;
		double current_gamma = 0;
		double current_vega = 0;
		for (String sym: knownSymbols) {
			current_delta = current_delta + (positions.get(sym)*deltas.get(sym));
			current_gamma = current_gamma + (positions.get(sym)*gammas.get(sym));
			current_vega = current_vega + (positions.get(sym)*vegas.get(sym));
		}
		/*log("Calculating net fucking positions " );
        
        log("Current Delta: " + current_delta);
        log("Current Gamma: " + current_gamma);
        log("Current Vega: " + current_vega);*/
        
		// Change the risk to suit risk parameters
		double comfortParameter = 0.9;
		double comfortDeltaMin = comfortParameter * minDelta;
		double comfortDeltaMax = comfortParameter * maxDelta;
		double comfortVegaMin = comfortParameter * minVega;
		double comfortVegaMax = comfortParameter * maxVega;
		double comfortGammaMin = comfortParameter * minGamma;
		double comfortGammaMax = comfortParameter * maxGamma;
		
		double desiredDelta;
		double desiredVega;
		double desiredGamma;
		
		if (forecastDelta < comfortDeltaMin)
		{	
			desiredDelta = comfortDeltaMin;
		}
		else if(forecastDelta > comfortDeltaMax)
		{
			desiredDelta = comfortDeltaMax;
		}
		else 
		{
			desiredDelta = forecastDelta;
		}
		if (forecastVega < comfortVegaMin)
		{	
			desiredVega = comfortVegaMin;
		}
		else if(forecastVega > comfortVegaMax)
		{
			desiredVega = comfortVegaMax;
		}
		else 
		{
			desiredVega = forecastVega;
		}
		if (forecastGamma < comfortGammaMin)
		{	
			desiredGamma = comfortGammaMin;
		}
		else if(forecastGamma > comfortGammaMax)
		{
			desiredGamma = comfortGammaMax;
		}
		else 
		{
			desiredGamma = forecastGamma;
		}
		
		// Substract from current position to obtain the desired change in greeks
		desiredDelta = desiredDelta - current_delta;
		desiredVega = desiredVega - current_vega;
		desiredGamma = desiredGamma - current_gamma;
		
        log("DELTA: " + Double.toString(desiredDelta));
        
		double bestPrice = Double.NEGATIVE_INFINITY;
        int best_c1 = 0;
        int best_c2 = 0;
        int best_underlyingAmount = 0;
        String best_p1 = "";
        String best_p2 = "";
        
        // solve problem with constraints
        for (int i = 0; i < knownSymbols.size(); i++) {
        	for (int j = i+1; j < knownSymbols.size(); j++) {
        		String p1 = knownSymbols.get(i);
        		String p2 = knownSymbols.get(j);

        		double gamma1 = gammas.get(p1);
        		double gamma2 = gammas.get(p2);
        		double vega1 = vegas.get(p1);
        		double vega2 = vegas.get(p2);
                
                //log("Gamma1: " + gamma1 + " Gamma2: " + gamma2 + " Vega1: " + vega1 + " Vega2: " + vega2);
                
        		double det = (gamma1*vega2 - gamma2*vega1);
                //log("det = " + det);
				if (det != 0)
				{
					double c1 = (1.0/det)*(vega2*desiredGamma - gamma2*desiredVega);
					double c2 = (1.0/det)*(-vega1*desiredGamma + gamma1*desiredVega);
					
                    log("c1: " + c1);
                    log("c2: " + c2);
                    
					int roundc1 = (int) c1;
                    //log ("This is fucking c1   " + c1);
					int roundc2 = (int) c2;
                    //log("This is fucking c2   " +c2);
					double cash = 0; //amount of cash we will receive in this transaction

					if (roundc1 > 0) {
						cash = cash - roundc1 * asks.get(p1);
					}
					else {
						cash = cash + roundc1 * bids.get(p1);
					}

					if (roundc2 > 0) {
						cash = cash - roundc2 * asks.get(p2);
					}
					else {
						cash = cash + roundc2 * bids.get(p2);
					}
					int underlyingAmount = (int) ((desiredDelta -(roundc1*deltas.get(p1) + roundc2*deltas.get(p2))));
                    //log("This is fucking underlying Amount    " + underlyingAmount);
					if (underlyingAmount < 0) {
						cash = cash + underlyingAmount * bids.get(underlyingSymbol);
					}
					else {
						cash = cash - underlyingAmount * asks.get(underlyingSymbol);
					}
                    //log ("This is the fucking cash man, cash " + cash);
					if (cash > bestPrice) {
						log("Test message:" + p1);
                        bestPrice = cash;
						best_c1 = roundc1;
						best_c2 = roundc2;
						best_underlyingAmount = underlyingAmount;
						best_p1 = p1;
						best_p2 = p2;
					}
				}
        	}
        }

        // return solution
        SolutionObject solution = new SolutionObject(bestPrice, best_c1, best_c2, best_underlyingAmount, best_p1, best_p2);
        return solution;   
    }

	public OrderInfo[] placeOrders() {
    OrderInfo[] orders = new OrderInfo[knownSymbols.size()];
    SolutionObject solution = findOptimalCombination();
/*
		log("Placing orders");
        log("Current cash:" + money);
		

		double bestPrice = solution.bestPrice;
		int best_c1 = solution.best_c1;
		int best_c2 = solution.best_c2;
		int best_underlyingAmount = solution.best_underlyingAmount;
		String p1 = solution.best_p1;
		String p2 = solution.best_p2;

		
		/*for (int i = 0; i < knownSymbols.size(); i++) {
			String symbol = knownSymbols.get(i);
			orders[i] = new OrderInfo(symbol, OrderSide.BUY, 100.00, 10);
		}*/ /*
        if (best_c1 != 0 || best_c2 != 0) {
            log("Best_c1:" + best_c1);
            log("Best_c2:" + best_c2);
        }*/
        
                HashMap<String, Double> deltas = new HashMap<String, Double>();
        HashMap<String, Double> gammas = new HashMap<String, Double>();
        HashMap<String, Double> vegas = new HashMap<String, Double>();
        HashMap<String, Double> prices = new HashMap<String, Double>();
        log("test");
        if (time < 1) {
        	for (String sym: knownSymbols) {
        		InstrumentDetails details = instruments().getInstrumentDetails(sym);
        		if (!details.type.isOption()) {
        			underlyingSymbol = sym;
        		}			
        	}
        }
        
        
                for (String sym: knownSymbols) {
            InstrumentDetails details = instruments().getInstrumentDetails(sym);
            if (details.type.isOption()){
                //note: here we use midpoint of the spot. note convexity of the greeks/price functions(?)
                double spot = (bids.get(underlyingSymbol) + asks.get(underlyingSymbol)) / 2.0;
                double timeToExpiry = (100.0-time)/365.0;
                Double delta = Optionsutil.calculateDelta(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Underlying Delta:" + delta);
                Double gamma = Optionsutil.calculateGamma(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Gamma: " + gamma);
                Double vega = Optionsutil.calculateVega(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                //log("Vega: " + vega);	
                Double price = Optionsutil.Call(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                deltas.put(sym, delta);
                gammas.put(sym, gamma);
                vegas.put(sym, vega);
                prices.put(sym, price);
            }
            // also put in underlying as a 1 delta, 0 vega, 0 gamma instrument
            else {
            	underlyingSymbol = sym;
                deltas.put(sym, 1.0);
                gammas.put(sym, 0.0);
                vegas.put(sym, 0.0);
                prices.put(sym, (bids.get(sym)+asks.get(sym)) / 2.0);
            }
        }
        
        
		for (int i = 0; i < knownSymbols.size(); i++) {
			String symbol = knownSymbols.get(i);
	/*		if (symbol.equals(p1)) {
				if (best_c1 > 0) {
					orders[i] = new OrderInfo(symbol, OrderSide.BUY, asks.get(symbol), (int)(best_c1));
                    log("Sending buy order for " + ((int) best_c1) + " " + symbol + " at " + asks.get(symbol)); 
				}
				else {
					orders[i] = new OrderInfo(symbol, OrderSide.SELL, bids.get(symbol), (int)(-1*best_c1));
                    log("Sending sell order for " + ((int) (-1*best_c1)) + " " + symbol + " at " + bids.get(symbol)); 
				}
			}
			else if (symbol.equals(p2)) {
				if (best_c2 > 0) {
					orders[i] = new OrderInfo(symbol, OrderSide.BUY, asks.get(symbol), (int)(best_c2));
                    log("Sending buy order for " + ((int) best_c2) + " " + symbol + " at " + asks.get(symbol)); 
				}
				else {
					orders[i] = new OrderInfo(symbol, OrderSide.SELL, bids.get(symbol), (int)(-1*best_c2));
                    log("Sending sell order for " + ((int) (-1*best_c2)) + " " + symbol + " at " + bids.get(symbol)); 
				}
			}
			else */if (symbol.equals(underlyingSymbol)) {/*
				if (best_underlyingAmount > 0){
					orders[i] = new OrderInfo(symbol, OrderSide.BUY, asks.get(symbol), (int)(best_underlyingAmount));
                    log("Sending buy order for " + ((int) best_underlyingAmount) + " " + symbol + " at " + asks.get(symbol)); 
				}
				else {
					orders[i] = new OrderInfo(symbol, OrderSide.SELL, bids.get(symbol), (int)(-1*best_underlyingAmount));
                    log("Sending sell order for " + ((int) (-1*best_underlyingAmount)) + " " + symbol + " at " + bids.get(symbol)); 
				}*/
                
                		double current_delta = 0;
		double current_gamma = 0;
		double current_vega = 0;
		for (String sym: knownSymbols) {
			current_delta = current_delta + (positions.get(sym)*deltas.get(sym));
			current_gamma = current_gamma + (positions.get(sym)*gammas.get(sym));
			current_vega = current_vega + (positions.get(sym)*vegas.get(sym));
		}
                
                		double comfortParameter = 0.9;
		double comfortDeltaMin = comfortParameter * minDelta;
		double comfortDeltaMax = comfortParameter * maxDelta;
		double comfortVegaMin = comfortParameter * minVega;
		double comfortVegaMax = comfortParameter * maxVega;
		double comfortGammaMin = comfortParameter * minGamma;
		double comfortGammaMax = comfortParameter * maxGamma;
		
		double desiredDelta;
		double desiredVega;
		double desiredGamma;
		
		if (forecastDelta < comfortDeltaMin)
		{	
			desiredDelta = comfortDeltaMin;
		}
		else if(forecastDelta > comfortDeltaMax)
		{
			desiredDelta = comfortDeltaMax;
		}
		else 
		{
			desiredDelta = forecastDelta;
		}
		if (forecastVega < comfortVegaMin)
		{	
			desiredVega = comfortVegaMin;
		}
		else if(forecastVega > comfortVegaMax)
		{
			desiredVega = comfortVegaMax;
		}
		else 
		{
			desiredVega = forecastVega;
		}
		if (forecastGamma < comfortGammaMin)
		{	
			desiredGamma = comfortGammaMin;
		}
		else if(forecastGamma > comfortGammaMax)
		{
			desiredGamma = comfortGammaMax;
		}
		else 
		{
			desiredGamma = forecastGamma;
		}
		
		// Substract from current position to obtain the desired change in greeks
		desiredDelta = desiredDelta - current_delta;
		desiredVega = desiredVega - current_vega;
		desiredGamma = desiredGamma - current_gamma;
        
        log(Double.toString(desiredDelta));
        
                if(desiredDelta>0) {
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, asks.get(symbol), (int)(desiredDelta));
                }
                else {
                    orders[i] = new OrderInfo(symbol, OrderSide.SELL, bids.get(symbol), (int)(-1*desiredDelta));
                }
                }
			else {
				continue;
			}
        }
		return orders;
	}

	public void orderFilled(String idSymbol, double price2, int quantity) {
        // update our positions
        if (positions.containsKey(idSymbol)) {
            positions.put(idSymbol, positions.get(idSymbol) + quantity);
        }
        else {
            positions.put(idSymbol, quantity);
        }
		log("My order for " + idSymbol + " got filled at " + price2 + " with quantity of " + quantity);
        money = money - (price2*quantity);
        pnl = 0;
        deltapos = 0;
        log("Current cash:" + money);
        for (int i = 0; i < knownSymbols.size(); i++) {
            temp = knownSymbols.get(i);
            pnl += positions.get(temp) * ((bids.get(temp)+asks.get(temp))/2);
            log("Position in " + temp + " is " + positions.get(temp));
        }
        log("Current PnL:" + pnl);
        
        HashMap<String, Double> deltas = new HashMap<String, Double>();
        HashMap<String, Double> gammas = new HashMap<String, Double>();
        HashMap<String, Double> vegas = new HashMap<String, Double>();
        HashMap<String, Double> prices = new HashMap<String, Double>();

        for (String sym: knownSymbols) {
            InstrumentDetails details = instruments().getInstrumentDetails(sym);
            // calculate price and greeks for options
            if (details.type.isOption()) {
                //note: here we use midpoint of the spot. note convexity of the greeks/price functions(?)
                double spot = (bids.get(sym) + asks.get(sym)) / 2.0;
                double timeToExpiry = (100.0-time)/365.0;
                Double delta = Optionsutil.calculateDelta(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                Double gamma = Optionsutil.calculateGamma(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                Double vega = Optionsutil.calculateVega(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                Double price = Optionsutil.Call(spot, details.strikePrice, timeToExpiry, RATE, impliedVol);
                deltas.put(sym, delta);
                gammas.put(sym, gamma);
                vegas.put(sym, vega);
                prices.put(sym, price);
            }
            // also put in underlying as a 1 delta, 0 vega, 0 gamma instrument
            else {
            	underlyingSymbol = sym;
                deltas.put(sym, 1.0);
                gammas.put(sym, 0.0);
                vegas.put(sym, 0.0);
                prices.put(sym, (bids.get(sym)+asks.get(sym)) / 2.0);
            }
        }
        double current_delta = 0;
		double current_gamma = 0;
		double current_vega = 0;
		for (String sym: knownSymbols) {
			current_delta = current_delta + (positions.get(sym)*deltas.get(sym));
			current_gamma = current_gamma + (positions.get(sym)*gammas.get(sym));
			current_vega = current_vega + (positions.get(sym)*vegas.get(sym));
		}
        log("Current Delta: " + current_delta);
        log("Current Gamma: " + current_gamma);
        log("Current Vega: " + current_vega);
	}

	public OptionsCase getOptionCaseImplementation() {
		return this;
	}
}





