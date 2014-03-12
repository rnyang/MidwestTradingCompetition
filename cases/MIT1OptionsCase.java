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

public class MIT1OptionsCase extends AbstractOptionsCase implements OptionsCase {
	
	
	private IDB myDatabase;
	int factor;
	int failSafe; 
	int maxIter = 1000; 
	private List<String> knownSymbols = new ArrayList<String>();
	
	 //Option prices and greeks
    private HashMap<String, Double[]> prices = new HashMap<String, Double[]>(); 
    private HashMap<String, Double> deltas = new HashMap<String, Double>(); 
    private HashMap<String, Double> gammas = new HashMap<String, Double>();
    private HashMap<String, Double> vegas = new HashMap<String, Double>();
    private HashMap<String, Double> ratios = new HashMap<String, Double>(); 
    
    //positions
    private HashMap<String, Integer> position = new HashMap<String, Integer>(); 
    
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
    private ArrayList<OrderInfo> orders = new ArrayList<OrderInfo>(); 
    
    //random number generator
    private Random randomGenerator = new Random(); 
    
    //initialization flag
    private boolean initialized = false; 
    
    //pnl
    private double PNL = 0.0; 
    private double vegaPNL = 0.0;
    private ArrayList<Double> PNLarray = new ArrayList<Double>(); 
    
    //penalty flag
    private boolean penalty = false; 
    
    //first run flag
    private boolean firstRun = true; 
    
    //adjust this for max iterations
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		setup.addVariable("failSafe", "turn off trading", "int", "0"); 
		setup.addVariable("maxIter", "number of gradient descent iters", "int", "1000"); 
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
		failSafe = getIntVar("failSafe"); 
		maxIter  = getIntVar("maxIter"); 
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
        if (knownSymbols.contains(idSymbol) == false) {
            knownSymbols.add(idSymbol);
        }
        if (knownSymbols.size() > 10) {
        	if (idSymbol.equals("MIT1~RAND-E")) {
        		double oldBid = prices.get("MIT1~RAND-E")[0]; 
        		double oldAsk = prices.get("MIT1~RAND-E")[1]; 
        		double spotChange = (((bid+ask)/2.0) - ((oldBid+oldAsk)/2.0));
        		double deltaPNL = portfolioDelta * spotChange;  
        		double gammaPNL = portfolioGamma * (0.5)*Math.pow(spotChange,2); 
        		PNL = deltaPNL + gammaPNL; 
        		PNLarray.add(PNL); 
        	}
        }
        
        prices.put(idSymbol, new Double[] {bid, ask}); 

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
        
        log("MIN DELTA IS: " + minDelta); 
        log("MAX DELTA IS: " + maxDelta); 
        
        if (knownSymbols.size() > 10) {
        	this.optimizePortfolio(); 
        }
	}

	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");
		forecastDelta = msg.delta;
        forecastVega = msg.vega;
        forecastGamma = msg.gamma; 
        log("FORECASTGAMMA: " + String.valueOf(forecastVega));
        log("FORECASTVEGA: " + String.valueOf(forecastGamma)); 
        log("CURGAMMA: " + String.valueOf(portfolioGamma));
        log("CURVEGA: " + String.valueOf(portfolioVega)); 
        if (knownSymbols.size() > 10) {
        	this.optimizePortfolio(); 
        }
        
	}
	
	public void newVolUpdate(VolUpdate msg) {
		
		if (tick == 1) {
			Object[] positionOptions = position.keySet().toArray(); 
			for (int i=0; i < positionOptions.length; i++) {
				String option = positionOptions[i].toString(); 
				log(option + " " + String.valueOf(position.get(option))); 
			}
		}
		vegaPNL = portfolioVega * (msg.impliedVol - vol); 
		double totalPNL = vegaPNL; 
		for (int i=0; i < PNLarray.size(); i++) {
			totalPNL += PNLarray.get(i); 
		}
		log("PNL: " + String.valueOf(totalPNL));
		PNLarray.clear(); 
        vol = msg.impliedVol; 
        tick -= 1.0; 
        if (knownSymbols.size() > 10) {
            initialized = true;
            //log("WENT TRUE");
        }
        if (initialized == true) {
            this.updateOptionGreeks(); 
        }
        //log("Updated volatility forecast"); 
    }
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		if (position.containsKey(idSymbol)) {
			position.put(idSymbol, position.get(idSymbol) + quantity); 
		} else {
			position.put(idSymbol, quantity); 
		}
		
		if (idSymbol.equals("MIT1~RAND-E")) {
			portfolioDelta += quantity; 
			//log("DELTA: " + String.valueOf(portfolioDelta)); 
		} 
		
		else {
			//update portfolio greeks based on new position
			double spot = (prices.get("MIT1~RAND-E")[0] + prices.get("MIT1~RAND-E")[1])/2.0;
			Object[] positionOptions = position.keySet().toArray(); 
			portfolioDelta = 0.0;
			portfolioVega = 0.0;
			portfolioGamma = 0.0; 
			for (int i=0; i < positionOptions.length; i++) {
				String option = positionOptions[i].toString(); 
				if (option.equals("MIT1~RAND-1")) {
					portfolioDelta += position.get(option); 
					continue;
				}
				InstrumentDetails details = instruments().getInstrumentDetails(option);
				portfolioDelta += position.get(option) * Optionsutil.calculateDelta(spot, details.strikePrice, tick/365.0, rate, vol); 
				portfolioVega += position.get(option) * Optionsutil.calculateVega(spot, details.strikePrice, tick/365.0, rate, vol); 
				portfolioGamma += position.get(option) * Optionsutil.calculateGamma(spot, details.strikePrice, tick/365.0, rate, vol); 
			
			}
			
			
			log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			log(String.valueOf(portfolioDelta)); 
			log(String.valueOf(portfolioGamma)); 
			log(String.valueOf(portfolioVega)); 
			log("GAMMA_DESIRED " + String.valueOf(forecastGamma)); 
			log("VEGA_DESIRED " + String.valueOf(forecastVega)); 
			log("MIN_VEGA " + String.valueOf(minVega));
			log("MAX_VEGA " + String.valueOf(maxVega)); 
			log("MIN_GAMMA" + String.valueOf(minGamma));
			log("MAX_GAMMA" + String.valueOf(maxGamma)); 
			log("MIN_DELTA " + String.valueOf(minDelta));
			log("MAX_DELTA " + String.valueOf(maxDelta)); 
			
			//hedge 
			if (!idSymbol.equals("MIT1~RAND-E")) {
				//log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			}
			//log("DELTA: " + String.valueOf(portfolioDelta) +  " | VEGA: " + String.valueOf(portfolioVega) + " | GAMMA: " + String.valueOf(portfolioGamma)); 
			//log("FORECAST_DELTA: " + String.valueOf(forecastDelta) +  " | FORECAST_VEGA: " + String.valueOf(forecastVega) + " | FORECAST_GAMMA: " + String.valueOf(forecastGamma)); 
		}
		log("Penalty called...oh no!");
		penalty = true;
		this.optimizePortfolio(); 
	}

	public OrderInfo[] placeOrders() {
		if (failSafe == 0) {
			if (firstRun == true && knownSymbols.size() > 10) {
				this.updateOptionGreeks();
				this.optimizePortfolio();
				firstRun = false; 
			}
		    log("Placing orders");
	        OrderInfo[] liveOrders = new OrderInfo[orders.size()];
	        log(String.valueOf(orders.size())); 
	        for (int i = 0; i < orders.size(); i++) {
	            liveOrders[i] = orders.get(i); 
	        }
	        orders.clear(); 
	        return liveOrders;
		}
		else {
			orders.clear(); 
			OrderInfo[] liveOrders = new OrderInfo[1]; 
			return liveOrders; 
		}
    }

	public void orderFilled(String idSymbol, double price, int quantity) {
		
		if (position.containsKey(idSymbol)) {
			position.put(idSymbol, position.get(idSymbol) + quantity); 
		} else {
			position.put(idSymbol, quantity); 
		}
		
		if (idSymbol.equals("MIT1~RAND-E")) {
			portfolioDelta += quantity; 
			//log("DELTA: " + String.valueOf(portfolioDelta)); 
		} 
		
		else {
			//update portfolio greeks based on new position
			double spot = (prices.get("MIT1~RAND-E")[0] + prices.get("MIT1~RAND-E")[1])/2.0;
			Object[] positionOptions = position.keySet().toArray(); 
			portfolioDelta = 0.0;
			portfolioVega = 0.0;
			portfolioGamma = 0.0; 
			for (int i=0; i < positionOptions.length; i++) {
				String option = positionOptions[i].toString(); 
				if (option.equals("MIT1~RAND-1")) {
					portfolioDelta += position.get(option); 
					continue;
				}
				InstrumentDetails details = instruments().getInstrumentDetails(option);
				portfolioDelta += position.get(option) * Optionsutil.calculateDelta(spot, details.strikePrice, tick/365.0, rate, vol); 
				portfolioVega += position.get(option) * Optionsutil.calculateVega(spot, details.strikePrice, tick/365.0, rate, vol); 
				portfolioGamma += position.get(option) * Optionsutil.calculateGamma(spot, details.strikePrice, tick/365.0, rate, vol); 
			
			}
			
			
			log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			log(String.valueOf(portfolioDelta)); 
			log(String.valueOf(portfolioGamma)); 
			log(String.valueOf(portfolioVega)); 
			log("GAMMA_DESIRED " + String.valueOf(forecastGamma)); 
			log("VEGA_DESIRED " + String.valueOf(forecastVega)); 
			log("MIN_VEGA " + String.valueOf(minVega));
			log("MAX_VEGA " + String.valueOf(maxVega)); 
			
			
			//hedge 
			if (!idSymbol.equals("MIT1~RAND-E")) {
				//log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			}
			//log("DELTA: " + String.valueOf(portfolioDelta) +  " | VEGA: " + String.valueOf(portfolioVega) + " | GAMMA: " + String.valueOf(portfolioGamma)); 
			//log("FORECAST_DELTA: " + String.valueOf(forecastDelta) +  " | FORECAST_VEGA: " + String.valueOf(forecastVega) + " | FORECAST_GAMMA: " + String.valueOf(forecastGamma)); 
		}
	}
	
	public void updateOptionGreeks() {
        for (int i=0; i < knownSymbols.size(); i++) {
            if (!knownSymbols.get(i).equals("MIT1~RAND-E")) {
                InstrumentDetails details = instruments().getInstrumentDetails(knownSymbols.get(i)); 
                double spot = (prices.get("MIT1~RAND-E")[0] + prices.get("MIT1~RAND-E")[1])/2.0;
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
		
		//get all option names 
		ArrayList<String> optionNames = new ArrayList<String>(); 
		for (int i=0; i < knownSymbols.size(); i++) {
			if (!knownSymbols.get(i).equals("MIT1~RAND-E")) {
				optionNames.add(knownSymbols.get(i)); 
			}
		} 
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
				double gammaDifference = portfolioGamma - (maxGamma*0.99); 
				double optionGamma = gammas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.01, (int)Math.ceil(gammaDifference/optionGamma))); 
				double optionDelta = deltas.get(bestRatioOption); 
				int roundedDelta = (int)Math.round(optionDelta); 
				if (roundedDelta > 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
				} else if (roundedDelta < 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
				}
			}
			
			else if (portfolioGamma < minGamma) {
				double gammaDifference = (minGamma*1.01) - portfolioGamma; 
				double optionGamma = gammas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 10000.0, (int)Math.ceil(gammaDifference/optionGamma)));
				double optionDelta = deltas.get(bestRatioOption); 
				int roundedDelta = (int)Math.round(optionDelta); 
				if (roundedDelta > 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
				} else if (roundedDelta < 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
				}
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
				double vegaDifference = portfolioVega - (maxVega*0.99); 
				double optionVega = vegas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.01, (int)Math.ceil(vegaDifference/optionVega))); 
				double optionDelta = deltas.get(bestRatioOption); 
				int roundedDelta = (int)Math.round(optionDelta) * (int)Math.ceil(vegaDifference/optionVega); 
				if (roundedDelta > 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
				} else if (roundedDelta < 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
				}
			}
			
			else if (portfolioVega < minVega) {
				double vegaDifference = (minVega*1.01) - portfolioVega; 
				double optionVega = vegas.get(bestRatioOption); 
				orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 10000.0, (int)Math.ceil(vegaDifference/optionVega)));
				double optionDelta = deltas.get(bestRatioOption); 
				int roundedDelta = (int)Math.round(optionDelta) * (int)Math.ceil(vegaDifference/optionVega); 
				if (roundedDelta > 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
				} else if (roundedDelta < 0) {
					orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
				}
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
			
			double vegaDifference = portfolioVega - (maxVega*0.99);
			double optionVega = vegas.get(bestRatioOption);
			
			double gammaDifference = portfolioGamma - (maxGamma*0.99);
			double optionGamma = vegas.get(bestRatioOption); 
			
			double numVegaOptions = Math.ceil(vegaDifference/optionVega); 
			double numGammaOptions = Math.ceil(gammaDifference/optionGamma); 
			orders.add(new OrderInfo(bestRatioOption, OrderSide.SELL, 0.01, (int)Math.max(numVegaOptions, numGammaOptions))); 	
			double optionDelta = deltas.get(bestRatioOption); 
			int roundedDelta = (int)Math.round(optionDelta) * (int)Math.max(numVegaOptions, numGammaOptions); 
			if (roundedDelta > 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
			} else if (roundedDelta < 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
			}
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
			
			double vegaDifference = (minVega*1.01) - portfolioVega; 
			double optionVega = vegas.get(bestRatioOption);
			
			double gammaDifference = (minGamma*1.01) - portfolioGamma;
			double optionGamma = vegas.get(bestRatioOption); 
			
			double numVegaOptions = Math.ceil(vegaDifference/optionVega); 
			double numGammaOptions = Math.ceil(gammaDifference/optionGamma); 
			orders.add(new OrderInfo(bestRatioOption, OrderSide.BUY, 100000.0, (int)Math.max(numVegaOptions, numGammaOptions)));
			double optionDelta = deltas.get(bestRatioOption); 
			int roundedDelta = (int)Math.round(optionDelta) * (int)Math.max(numVegaOptions, numGammaOptions);; 
			if (roundedDelta > 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
			} else if (roundedDelta < 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
			}
		}
		
		if (penalty==false) {
			
			log("MAX ITER: " + String.valueOf(maxIter)); 
			log("OPTIMIZING");
			//theoretical greeks
			double theoGamma = portfolioGamma;
			double theoVega = portfolioVega; 
			
			boolean run = false; 
			//maximum iterations
			log("OPTIM_GAMMA: " + String.valueOf(portfolioGamma)); 
			log("OPTIM_VEGA: " + String.valueOf(portfolioVega)); 
			log("OPTIM_PORT_GAMMA: " + String.valueOf(forecastGamma)); 
			log("OPTIM_PORT_VEGA: " + String.valueOf(forecastVega)); 
			if (portfolioGamma >= forecastGamma && portfolioVega >= forecastVega && run==false) {
				run = true; 
				double desiredGamma = Math.max(minGamma, forecastGamma); 
				double desiredVega = Math.max(minVega, forecastVega); 
				double cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
				double oldcost = cost; 
				int count = 0; 
				log("WE GOT HERE"); 
				while (cost <= oldcost) {
					
					if (count > maxIter) {
						break; 
					} 
					
					//log("OVER GREEKS"); 
					//log("OLDCOST: " + String.valueOf(oldcost) + "| NEWCOST: " + String.valueOf(cost)); 
					int randomIndex = randomGenerator.nextInt(optionNames.size()); 
					String randomOption = optionNames.get(randomIndex);
					double optionGamma = gammas.get(randomOption);
					double optionVega = vegas.get(randomOption); 
					double optionDelta = deltas.get(randomOption); 
					int roundedDelta = (int)Math.round(optionDelta); 
					theoGamma -= optionGamma;
					theoVega -= optionVega; 
					oldcost = cost; 
					cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
					//log("OLD_COST: " + String.valueOf(oldcost));
					//log("NEW_CST: " + String.valueOf(cost)); 
					orders.add(new OrderInfo(randomOption, OrderSide.SELL, 0.01, 1)); 
					if (roundedDelta > 0) {
						orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
					} else if (roundedDelta < 0) {
						orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
					}
					count++; 
					
						
				}
				
				
			} else if (portfolioGamma <= forecastGamma && portfolioVega <= forecastVega && run==false) {
				run = true; 
				double desiredGamma = Math.min(maxGamma, forecastGamma); 
				double desiredVega = Math.max(maxVega, forecastVega); 
				double cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
				double oldcost = cost; 
				int count = 0; 
				log("WE GOT HERE 2"); 
				while (cost <= oldcost) {
					
					if (count > maxIter) {
						break;
					}
					
					//log("UNDER GREEKS"); 
					//log("OLDCOST: " + String.valueOf(oldcost) + "| NEWCOST: " + String.valueOf(cost)); 
					int randomIndex = randomGenerator.nextInt(optionNames.size()); 
					String randomOption = optionNames.get(randomIndex); 
					double optionGamma = gammas.get(randomOption);
					double optionVega = vegas.get(randomOption); 
					double optionDelta = deltas.get(randomOption); 
					int roundedDelta = (int)Math.round(optionDelta); 
					theoGamma += optionGamma;
					theoVega += optionVega; 
					oldcost = cost; 
					cost = this.costFunction(theoGamma, theoVega, desiredGamma, desiredVega); 
					//log("OLD_COST2: " + String.valueOf(oldcost));
					//log("NEW_CST2: " + String.valueOf(cost)); 
					orders.add(new OrderInfo(randomOption, OrderSide.BUY, 10000.0, 1)); 
					if (roundedDelta > 0) {
						orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(optionDelta))); 
					} else if (roundedDelta < 0) {
						orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 1000.0, (int)Math.round(optionDelta))); 
					}
					count++; 
						
				}
			}
		}
		penalty = false; 
	}

	public double costFunction(double gamma, double vega, double desiredGamma, double desiredVega) {
		return (Math.pow((gamma - desiredGamma), 2) + Math.pow((vega - desiredVega), 2)); 
	}
	
	public void hedge() {
		if (portfolioDelta > 0) {
			double difference = Math.abs(portfolioDelta - 0.0); 
			int amount = (int)Math.round(difference); 
			if (amount != 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.SELL, 0.01, (int)Math.round(difference))); 
			}
		}
		else if (portfolioDelta < 0) {
			double difference = Math.abs(0.0 - portfolioDelta); 
			int amount = (int)Math.round(difference); 
			if (amount != 0) {
				orders.add(new OrderInfo("MIT1~RAND-E", OrderSide.BUY, 10000.0, (int)Math.round(difference))); 	
			}
		}
	}
	
	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
