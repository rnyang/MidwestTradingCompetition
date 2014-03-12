

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Queue; 
import java.util.LinkedList;
import java.util.Random;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.options.Optionsutil;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class MIT2Options extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();
	
	 //Option prices and greeks
    private HashMap<String, Double[]> prices = new HashMap<String, Double[]>(); 
    private HashMap<String, Double> deltas = new HashMap<String, Double>(); 
    private HashMap<String, Double> gammas = new HashMap<String, Double>();
    private HashMap<String, Double> vegas = new HashMap<String, Double>();
    private HashMap<String, Double> ratios = new HashMap<String, Double>(); 
    
    //current time
    private double tick = 100.0; 
    
    //pricing
    private double rate = 0.01; 
    
    //vol
    private double vol = 0.0; 

    //current positions
    private double portfolioVega = 0; 
    private double portfolioGamma = 0;
    private double portfolioDelta = 0; 
    
    //limits
    private double minDelta = 0;
    private double maxDelta = 0; 
    private double minGamma = 0;
    private double maxGamma = 0;
    private double minVega = 0;
    private double maxVega = 0; 
    
    //forecast
    private double forecastDelta = 0; 
    private double forecastGamma = 0; 
    private double forecastVega = 0; 
    
    //order queue
    ArrayList<OrderInfo> orders = new ArrayList<OrderInfo>(); 
    
    //random number generator
    private Random randomGenerator = new Random(); 
    
    //initialization flag
    private boolean initialized = false; 

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
        if (knownSymbols.contains(idSymbol) == false) {
            knownSymbols.add(idSymbol);
        }
        prices.put(idSymbol, new Double[] {bid, ask}); 
        //log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
        /*
        if (prices.keySet().contains("MIT2~RAND-E")) {
            log("The bid of the underlier is currently" + Double.toString((prices.get("MIT2~RAND-E")[0])));
            log("The ask of the underlier is currently" + Double.toString(prices.get("MIT2~RAND-E")[1]));
        }*/
    }

	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		minDelta = msg.minDelta;
        maxDelta = msg.maxDelta;
        minVega = msg.minVega;
        maxVega = msg.maxVega;
        minGamma = msg.minGamma;
        maxGamma = msg.maxGamma;
        
        if (knownSymbols.size() > 10) {
        	this.optimizePortfolio(); 
        }
	}

	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");
		forecastDelta = msg.delta;
        forecastVega = msg.vega;
        forecastGamma = msg.gamma; 
	}
	
	public void newVolUpdate(VolUpdate msg) {
        vol = msg.impliedVol; 
        tick -= 1.0; 
        if (knownSymbols.size() > 10) {
            initialized = true;
            //log("WENT TRUE");
        }
        if (initialized == true) {
            this.updateOptionGreeks();
            this.optimizePortfolio(); 
        }
        //log("Updated volatility forecast"); 
    }
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {

	    log("Placing orders");
        OrderInfo[] liveOrders = new OrderInfo[orders.size()];
        log(String.valueOf(orders.size())); 
        for (int i = 0; i < orders.size(); i++) {
            liveOrders[i] = orders.get(i); 
        }
        orders.clear(); 
        return liveOrders;
    }

	public void orderFilled(String idSymbol, double price, int quantity) {
		if (idSymbol.equals("MIT2~RAND-E")) {
			portfolioDelta += quantity; 
			log("DELTA: " + String.valueOf(portfolioDelta)); 
		} 
		
		else {
			//update portfolio greeks based on new position
			InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
			double spot = (prices.get("MIT2~RAND-E")[0] + prices.get("MIT2~RAND-E")[1])/2.0;
			portfolioDelta += quantity * Optionsutil.calculateDelta(spot, details.strikePrice, tick/365.0, rate, vol); 
			portfolioVega += quantity * Optionsutil.calculateVega(spot, details.strikePrice, tick/365.0, rate, vol); 
			portfolioGamma += quantity * Optionsutil.calculateGamma(spot, details.strikePrice, tick/365.0, rate, vol); 
        
			//hedge
			this.hedge(); 
			if (!idSymbol.equals("MIT2~RAND-E")) {
				//log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			}
			//log("DELTA: " + String.valueOf(portfolioDelta) +  " | VEGA: " + String.valueOf(portfolioVega) + " | GAMMA: " + String.valueOf(portfolioGamma)); 
			//log("FORECAST_DELTA: " + String.valueOf(forecastDelta) +  " | FORECAST_VEGA: " + String.valueOf(forecastVega) + " | FORECAST_GAMMA: " + String.valueOf(forecastGamma)); 
		}
	}
	
	public void updateOptionGreeks() {
        for (int i=0; i < knownSymbols.size(); i++) {
            if (!knownSymbols.get(i).equals("MIT2~RAND-E")) {
                InstrumentDetails details = instruments().getInstrumentDetails(knownSymbols.get(i)); 
                double spot = (prices.get("MIT2~RAND-E")[0] + prices.get("MIT2~RAND-E")[1])/2.0;
                double gamma = Optionsutil.calculateGamma(spot, details.strikePrice, tick/365.0, rate, vol); 
                double vega = Optionsutil.calculateVega(spot, details.strikePrice, tick/365.0, rate, vol); 
                double normalizedGamma = gamma/(maxGamma-minGamma); 
                double normalizedVega = vega/(maxVega-minVega); 
                deltas.put(knownSymbols.get(i), Optionsutil.calculateDelta(spot, details.strikePrice, tick/365.0, rate, vol));        
                gammas.put(knownSymbols.get(i), gamma);
                vegas.put(knownSymbols.get(i), vega);
                ratios.put(knownSymbols.get(i), normalizedGamma/normalizedVega); 
            }   
        }
    }
	
	public void optimizePortfolio() {
		
		log("OPTIMIZING");
		
		//get all option names 
		ArrayList<String> optionNames = new ArrayList<String>(); 
		for (int i=0; i < knownSymbols.size(); i++) {
			if (!knownSymbols.get(i).equals("MIT2~RAND-E")) {
				optionNames.add(knownSymbols.get(i)); 
			}
		} 
		this.hedge();
		
		//put vega and gamma within limits
		if (portfolioVega > minVega && portfolioVega < maxVega) {
			double maxRatio = 0.0; 
			int maxIndex = 0; 
			
			//find max ratio
			for (int i=0; i< optionNames.size(); i++) {
				double ratio = ratios.get(optionNames.get(i)); 
				if (ratio > maxRatio) {
					maxRatio = ratio; 
					maxIndex = i; 
				}
			}
			
			String bestRatioOption = optionNames.get(maxIndex);
			if (portfolioGamma > maxGamma) {
				double gammaDifference = portfolioGamma - (maxGamma*0.99999); 
				double optionGamma = gammas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.00001, (int)Math.ceil(gammaDifference/optionGamma))); 
			}
			
			else if (portfolioGamma < minGamma) {
				double gammaDifference = (minGamma*1.00001) - portfolioGamma; 
				double optionGamma = gammas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 10000.0, (int)Math.ceil(gammaDifference/optionGamma)));
			}
		}
		
		else if (portfolioGamma > minGamma && portfolioGamma < maxGamma) {
			double minRatio = 0.0; 
			int minIndex = 0; 
			
			//find min ratio
			for (int i=0; i< optionNames.size(); i++) {
				double ratio = ratios.get(optionNames.get(i)); 
				if (ratio < minRatio) {
					minRatio = ratio; 
					minIndex = i; 
				}
			}
			String bestRatioOption = optionNames.get(minIndex);
			
			if (portfolioVega > maxVega) {
				double vegaDifference = portfolioVega - (maxVega*0.99999); 
				double optionVega = vegas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.0001, (int)Math.ceil(vegaDifference/optionVega))); 
			}
			
			else if (portfolioVega < minVega) {
				double vegaDifference = (minVega*1.00001) - portfolioVega; 
				double optionVega = vegas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 10000.0, (int)Math.ceil(vegaDifference/optionVega)));
			}
		}
		
		else if (portfolioGamma > maxGamma && portfolioVega > maxVega) {
			double normalizedGammaOvershoot = (portfolioGamma - maxGamma)/(maxGamma-minGamma); 
			double normalizedVegaOvershoot = (portfolioVega - maxVega)/(maxVega-minVega); 
			double overshootGammaVegaRatio = normalizedGammaOvershoot/normalizedVegaOvershoot;
			
			double bestRatioDiff = 1000000000; 
			int bestIndex = 0; 
			
			//find best ratio
			for (int i=0; i< optionNames.size(); i++) {
				double ratio = ratios.get(optionNames.get(i)); 
				if (Math.abs((ratio - overshootGammaVegaRatio)) < bestRatioDiff) {
					bestRatioDiff = ratio - overshootGammaVegaRatio; 
					bestIndex = i; 
				}
			}
			String bestRatioOption = optionNames.get(bestIndex);
			
			double vegaDifference = portfolioVega - (maxVega*0.9999);
			double optionVega = vegas.get(bestRatioOption);
			
			double gammaDifference = portfolioGamma - (maxGamma*0.9999);
			double optionGamma = vegas.get(bestRatioOption); 
			
			double numVegaOptions = Math.ceil(vegaDifference/optionVega); 
			double numGammaOptions = Math.ceil(gammaDifference/optionGamma); 
			orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.0001, (int)Math.max(numVegaOptions, numGammaOptions))); 	
		}
		
		else if (portfolioGamma < minGamma && portfolioVega < minVega) {
			double normalizedGammaOvershoot = (minGamma - portfolioGamma)/(maxGamma-minGamma); 
			double normalizedVegaOvershoot = (minVega - portfolioVega)/(maxVega-minVega); 
			double overshootGammaVegaRatio = normalizedGammaOvershoot/normalizedVegaOvershoot;
			
			double bestRatioDiff = 1000000000; 
			int bestIndex = 0; 
			
			//find best ratio
			for (int i=0; i< optionNames.size(); i++) {
				double ratio = ratios.get(optionNames.get(i)); 
				if (Math.abs((ratio - overshootGammaVegaRatio)) < bestRatioDiff) {
					bestRatioDiff = ratio - overshootGammaVegaRatio; 
					bestIndex = i; 
				}
			}
			String bestRatioOption = optionNames.get(bestIndex);
			
			double vegaDifference = (minVega*1.00001) - portfolioVega; 
			double optionVega = vegas.get(bestRatioOption);
			
			double gammaDifference = (minGamma*1.000001) - portfolioGamma;
			double optionGamma = vegas.get(bestRatioOption); 
			
			double numVegaOptions = Math.ceil(vegaDifference/optionVega); 
			double numGammaOptions = Math.ceil(gammaDifference/optionGamma); 
			orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 100000.0, (int)Math.max(numVegaOptions, numGammaOptions)));
		}
	
		//theoretical greeks
		double theoGamma = portfolioGamma;
		double theoVega = portfolioVega; 
	
		if (portfolioGamma > forecastGamma || portfolioVega > forecastVega) {
			double desiredGamma = Math.max(minGamma, forecastGamma); 
			double desiredVega = Math.max(minVega, forecastVega); 
			double cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
			double oldcost = cost; 
			
			while (cost <= oldcost) {
				//log("OVER GREEKS"); 
				//log("OLDCOST: " + String.valueOf(oldcost) + "| NEWCOST: " + String.valueOf(cost)); 
				int randomIndex = randomGenerator.nextInt(optionNames.size()); 
				String randomOption = optionNames.get(randomIndex); 
				double optionGamma = gammas.get(randomOption);
				double optionVega = vegas.get(randomOption); 
				
				theoGamma -= optionGamma;
				theoVega -= optionVega; 
				oldcost = cost; 
				cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
				
				orders.add(new OrderInfo(randomOption, OrderSide.SELL, 0.00001, 1)); 
					
			}
			
			
		} else if (portfolioGamma < forecastGamma || portfolioVega < forecastVega) {
			double desiredGamma = Math.min(maxGamma, forecastGamma); 
			double desiredVega = Math.max(maxVega, forecastVega); 
			double cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
			double oldcost = cost; 
			
			while (cost <= oldcost) {
				//log("UNDER GREEKS"); 
				//log("OLDCOST: " + String.valueOf(oldcost) + "| NEWCOST: " + String.valueOf(cost)); 
				int randomIndex = randomGenerator.nextInt(optionNames.size()); 
				String randomOption = optionNames.get(randomIndex); 
				double optionGamma = gammas.get(randomOption);
				double optionVega = vegas.get(randomOption); 
				
				theoGamma += optionGamma;
				theoVega += optionVega; 
				oldcost = cost; 
				cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
				
				orders.add(new OrderInfo(randomOption, OrderSide.BUY, 10000.0, 1)); 
					
			}
		}
		
	}

	public double costFunction(double gamma, double vega, double desiredGamma, double desiredVega) {
		return (Math.pow((gamma - desiredGamma), 2) + Math.pow((vega - desiredVega), 2)); 
	}
	
	public void hedge() {
		if (portfolioDelta > maxDelta) {
			double difference = Math.abs(portfolioDelta - 0.0); 
			int amount = (int)Math.round(difference); 
			if (amount != 0) {
				orders.add(new OrderInfo("MIT2~RAND-E", OrderSide.SELL, 0.0001, (int)Math.round(difference))); 
			}
		}
		else if (portfolioDelta < minDelta) {
			double difference = Math.abs(portfolioDelta); 
			int amount = (int)Math.round(difference); 
			if (amount != 0) {
				orders.add(new OrderInfo("MIT2~RAND-E", OrderSide.BUY, 10000.0, (int)Math.round(difference))); 	
			}
		}
	}
	
	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
