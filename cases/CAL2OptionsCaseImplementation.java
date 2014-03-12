
import java.util.*;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.options.OrderInfo;

import org.chicago.cases.options.Optionsutil;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


public class CAL2OptionsCaseImplementation extends AbstractOptionsCase {
	
	class MySampleOptionImplementation implements OptionsCase {

		/* Constants */
		private static final double BACK_MONTH_TIME_TO_MATURITY = 130;
		private static final double FRONT_MONTH_TIME_TO_MATURITY = 100;
		private static final double INTEREST_RATE = .01;
		private static final double DAYS_PER_YEAR = 365;
		private static final double THRESHOLD = .001;
		private static final double MIN_CONTRACT_BID_PRICE = .05;
		private static final double TIME_THRESHOLD = 50;

		private IDB myDatabase;

		// Map for all objects that markets are given for
		private Map<String, Call> knownSymbols = new HashMap<String, Call>();
		// Map to store your current positions
		private Map<String, Integer> positions = new HashMap<String, Integer>();

		// Portfolio risk positions
		double myDelta = 0;
		double myGamma = 0;
		double myVega = 0;

		// greeks
		double forecastDelta;
		double forecastGamma;
		double forecastVega;

		// risk limits
		double minDelta;
		double maxDelta;
		double minGamma;
		double maxGamma;
		double minVega;
		double maxVega;

		// implied volatility	
		double impliedVol = 0;

		// current time 
		double time;

		// measures the current amount you have spent
		double capital;

		// signal to clear out of all positions
		Boolean liquidatePortfolio = false;
		// signal to stop trading
		Boolean tradingFlag = false;
		private static final int TRADING_ON = 1;
		// amount that we reduce risk limits by for our trading
		double RISKRATIO;
		// indicate liquidating portfolios when gettign new forecast
		Boolean liquidateAtForecast;
		// flag for stopping when reaching profit threshold
		Boolean stopWhenAhead;
		// lesser flag for stopping when reaching profit threshold
		Boolean stopWhenAheadEnough;
		// profit threshold
		double amountToStopAt;
		// lower profit threshold
		double amountToStopAtEnough;
		// loss threshold
		double LOSS_THRESHOLD;
		// negative slope linear function threshold
		double BOTTOM_THRESHOLD;

		public void addVariables(IJobSetup setup) {
			setup.addVariable("tradingFlag", "allow trading", "int", "1");
			setup.addVariable("riskRatio", "scale given risk parameters", "double", ".9");
			setup.addVariable("liquidateAtForecast", "liquidate current porfolio at forecast update", "int", "0");
			setup.addVariable("stopWhenAhead", "if you aren't feeling risky", "int", "1");
			setup.addVariable("stopWhenAheadEnough", "if you aren't feeling somewhat risky", "int", "1");
			setup.addVariable("amountToStopAt", "your threshold of risk", "double", "1000");
			setup.addVariable("amountToStopAtEnough", "your threshold of risk", "double", "500");
			setup.addVariable("LOSS_THRESHOLD", "amount to quit at", "double", "-500");
			setup.addVariable("BOTTOM_THRESHOLD", "bottom of declining slope cutoff", "double", "-500");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			tradingFlag = (getIntVar("tradingFlag") == TRADING_ON); 
			RISKRATIO = getDoubleVar("riskRatio");
			liquidateAtForecast = (getIntVar("liquidateAtForecast") == 1);
			stopWhenAhead = (getIntVar("stopWhenAhead") == 1);
			stopWhenAheadEnough = (getIntVar("stopWhenAheadEnough") == 1);
			amountToStopAt = getDoubleVar("amountToStopAt");
			amountToStopAtEnough = getDoubleVar("amountToStopAtEnough");
			LOSS_THRESHOLD = getDoubleVar("LOSS_THRESHOLD");
			BOTTOM_THRESHOLD = getDoubleVar("BOTTOM_THRESHOLD");
			// Initialize Variables
			time = 0;

		}

		public void newBidAsk(String idSymbol, double bid, double ask) {
			//knownSymbols.add(idSymbol);
			double timeToMaturity = getTimeToMaturity(idSymbol);
			double strikePrice = getStrikePrice(idSymbol);
			
			if (timeToMaturity != -1) {
				Call c = knownSymbols.get(idSymbol);
				if (c == null) {
					c = new Call(timeToMaturity, strikePrice, idSymbol, bid, ask);
					knownSymbols.put(idSymbol, c);
					if (isUnderlying(idSymbol)) {
						c.ourPrice = (bid + ask) / 2;
						c.ourDelta = 1;
						c.ourGamma = 0;
						c.ourVega = 0;
						c.valuesCalculated = true;
					}
				}
				else 
					c.setPrice(bid, ask);
				Integer d = positions.get(idSymbol);
				if (d == null)
					positions.put(idSymbol, 0);

				log("I received an update for " + c);

				// recalculate the greeks from update
				if (!isUnderlying(idSymbol)) {
					double timeLeft = c.expiryDate - time;
					double spot = getSpotPrice();

					c.ourPrice = Optionsutil.Call(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourDelta = Optionsutil.calculateDelta(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourGamma = Optionsutil.calculateGamma(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourVega = Optionsutil.calculateVega(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.valuesCalculated = true;
				}
				else {
					c.ourPrice = (bid + ask) / 2;
				}
				calculateGreeks();
			}
			else {
				log("Error found in newBidAsk()");
			}
		}

		public void newRiskMessage(RiskMessage msg) {
			minDelta = RISKRATIO * msg.minDelta;
			maxDelta = RISKRATIO * msg.maxDelta;
			minGamma = RISKRATIO * msg.minGamma;
			maxGamma = RISKRATIO * msg.maxGamma;
			minVega = RISKRATIO * msg.minVega;
			maxVega = RISKRATIO * msg.maxVega;

			log("Delta limits : [" + minDelta + ", " + maxDelta + "]");
			log("Gamma limits : [" + minGamma + ", " + maxGamma + "]");
			log("Vega limits : [" + minVega + ", " + maxVega + "]");
		}

		public void newForecastMessage(ForecastMessage msg) {
			forecastDelta = msg.delta;
			forecastGamma = msg.gamma;
			forecastVega = msg.vega;
			log("Forecast: delta = " + forecastDelta + ", gamma = " + forecastGamma + ", vega = " + forecastVega);
			
			if (liquidateAtForecast)
				liquidatePortfolio = true;
		}
		
		// Called once each time step, use this to price the greeks for all call options
		public void newVolUpdate(VolUpdate msg) {
			impliedVol = msg.impliedVol;
			log("Implied Volatility : " + impliedVol);

			
			double spot = getSpotPrice();
			if (spot != -1) {
				for (Map.Entry<String, Call> entry : knownSymbols.entrySet()) {
					String s = entry.getKey();
					Call c = entry.getValue();
					if (isUnderlying(s))
						continue;
					double timeLeft = c.expiryDate - time;
					c.ourPrice = Optionsutil.Call(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourDelta = Optionsutil.calculateDelta(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourGamma = Optionsutil.calculateGamma(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.ourVega = Optionsutil.calculateVega(spot, c.strikePrice, timeLeft / DAYS_PER_YEAR, INTEREST_RATE, impliedVol);
					c.valuesCalculated = true;
				}

				calculateGreeks();
			}
			else {
				log("Error occurred in getting spot price"); 
			}
			
			log("Current time : " + time + ", capital = " + capital + ", profit = " + calculateProfit());
			log("Current Position : Time = " + time + ", Delta = " + myDelta + ", Gamma = " + myGamma + ", Vega = " + myVega);
			time++;
		}

		public OrderInfo[] placeOrders() {

			if (!tradingFlag)
				return clearPortfolio();

			if (calculateProfit() < LOSS_THRESHOLD) {
				log ("lost too much already!");
				tradingFlag = false;
				return clearPortfolio();
			}
			// just quit if we are good
			if (stopWhenAhead && calculateProfit() >= amountToStopAt) {
				log ("we are so good that we will just stop now");
				tradingFlag = false;
				return clearPortfolio();
			}
			// linear decaying threshold for losses
			if (time >= TIME_THRESHOLD && stopWhenAheadEnough && (calculateProfit() >= (amountToStopAtEnough - (time - TIME_THRESHOLD) * (amountToStopAtEnough - BOTTOM_THRESHOLD) / (100 - TIME_THRESHOLD) )) ) {
				log ("time has passed and we are good enough with threshold being " + (amountToStopAtEnough - (time - TIME_THRESHOLD) * (amountToStopAtEnough - BOTTOM_THRESHOLD) / (100 - TIME_THRESHOLD)) );
				tradingFlag = false;
				return clearPortfolio();
			}

			if (liquidatePortfolio) {
				log("placeOrders liquidating at time " + time);
				liquidatePortfolio = false;
				return clearPortfolio();
			}

			/* check if you need to adjust positions to stay within limits */
			if (exceedingLimits()) 
				return adjustPortfolio();

			Map<Double, Call> dict = new HashMap<Double, Call>();
			ArrayList<Double> percentValues = new ArrayList<Double>();

			for (Map.Entry<String, Call> entry : knownSymbols.entrySet()) {
				String symbol = entry.getKey();
				if (isUnderlying(symbol)) // do not consider the underlying
					continue;
				Call c = entry.getValue();
				if (!c.valuesCalculated)
					continue;
				// get the percentDifference between our pricing and actual market value
				Double percentDifference = Math.abs(c.getPercentPriceDifference());
				dict.put(percentDifference, c);
				percentValues.add(percentDifference);
			}
			if (dict.size() != percentValues.size())
				log("The dictionary seems to be of different sizes, " + dict.size() + ", " + percentValues.size());

			// sort the array from largest to smallest
			Collections.sort(percentValues);
			Collections.reverse(percentValues);

			// Figure out the direction that you want to move to get towards forecast
			Boolean direction;
			// Use your current Delta Positions to adjust
			double projectedDelta = myDelta;
			double projectedGamma = myGamma;
			double projectedVega = myVega;

			// Map to keep track of what options you want to make orders for
			Map<String, Double> orderMap = new HashMap<String, Double>();

			// figure out what direction you need to get closer to the forecast delta
			direction = (projectedDelta < forecastDelta);

			for (Double value : percentValues) {
				Call c = dict.get(value);
				if (c.price.bid == 0.0)
					continue;
				Boolean positive = (c.ourDelta >= 0);

				// ignore insignificant double values
				if (Math.abs(c.ourDelta) < THRESHOLD)
					continue;

				double quantity = 0;
				while ((((projectedDelta < forecastDelta - c.ourDelta) && direction) || ((projectedDelta > forecastDelta + c.ourDelta) && !direction)) 
					&& (projectedDelta <= maxDelta) && (projectedDelta >= minDelta)
					&& (projectedGamma <= maxGamma) && (projectedGamma >= minGamma)
					&& (projectedVega <= maxVega) && (projectedVega >= minVega)) 
				{
					if ((direction && positive) || (!direction && !positive)) {
						projectedDelta += c.ourDelta;
						projectedGamma += c.ourGamma;
						projectedVega +=  c.ourVega;
						quantity++;
					}
					else {
						projectedDelta -= c.ourDelta;
						projectedGamma -= c.ourGamma;
						projectedVega -= c.ourVega;
						quantity--;
					}
				}

				/* adjust by one so it doesn't actually exceed limits */
				if (quantity > 0) {
					projectedDelta -= c.ourDelta;
					projectedGamma -= c.ourGamma;
					projectedVega -= c.ourVega;
					quantity--;
				}
				else if (quantity < 0) {
					projectedDelta += c.ourDelta;
					projectedGamma += c.ourGamma;
					projectedVega +=  c.ourVega;
					quantity++;
				}

				// add the values to the order map
				if (quantity != 0) {
					Double oldValue = orderMap.get(c.id);
					if (oldValue == null) 
						orderMap.put(c.id, quantity);
					else
						orderMap.put(c.id, oldValue + quantity);
				}
			}
			
			// Now do the same for gamma values
			direction = (projectedGamma < forecastGamma);

			for (Double value : percentValues) {
				Call c = dict.get(value);
				if (c.price.bid == 0.0)
					continue;
				Boolean positive = (c.ourGamma >= 0);

				// ignore insignificant double values
				if (Math.abs(c.ourGamma) < THRESHOLD)
					continue;

				double quantity = 0;
				while ((((projectedGamma < forecastGamma - c.ourGamma) && direction) || ((projectedGamma > forecastGamma + c.ourGamma) && !direction)) 
					&& (projectedDelta <= maxDelta) && (projectedDelta >= minDelta)
					&& (projectedGamma <= maxGamma) && (projectedGamma >= minGamma)
					&& (projectedVega <= maxVega) && (projectedVega >= minVega)) 
				{
					if ((direction && positive) || (!direction && !positive)) {
						projectedDelta += c.ourDelta;
						projectedGamma += c.ourGamma;
						projectedVega +=  c.ourVega;
						quantity++;
					}
					else {
						projectedDelta -= c.ourDelta;
						projectedGamma -= c.ourGamma;
						projectedVega -= c.ourVega;
						quantity--;
					}
				}

				/* adjust by one so it doesn't actually exceed limits */
				if (quantity > 0) {
					projectedDelta -= c.ourDelta;
					projectedGamma -= c.ourGamma;
					projectedVega -= c.ourVega;
					quantity--;
				}
				else if (quantity < 0) {
					projectedDelta += c.ourDelta;
					projectedGamma += c.ourGamma;
					projectedVega +=  c.ourVega;
					quantity++;
				}

				// add the values to the order map
				if (quantity != 0) {
					Double oldValue = orderMap.get(c.id);
					if (oldValue == null) 
						orderMap.put(c.id, quantity);
					else
						orderMap.put(c.id, oldValue + quantity);
				}
			}

			// now the same for vega
			direction = (projectedVega < forecastVega);

			for (Double value : percentValues) {
				Call c = dict.get(value);
				if (c.price.bid == 0.0)
					continue;
				Boolean positive = (c.ourVega >= 0);

				// ignore insignificant double values
				if (Math.abs(c.ourVega) < THRESHOLD)
					continue;

				double quantity = 0;
				while ((((projectedVega < forecastVega - c.ourVega) && direction) || ((projectedVega > forecastVega + c.ourVega) && !direction)) 
					&& (projectedDelta <= maxDelta) && (projectedDelta >= minDelta)
					&& (projectedGamma <= maxGamma) && (projectedGamma >= minGamma)
					&& (projectedVega <= maxVega) && (projectedVega >= minVega)) 
				{
					
					if ((direction && positive) || (!direction && !positive)) {
						projectedDelta += c.ourDelta;
						projectedGamma += c.ourGamma;
						projectedVega +=  c.ourVega;
						quantity++;
					}
					else {
						projectedDelta -= c.ourDelta;
						projectedGamma -= c.ourGamma;
						projectedVega -= c.ourVega;
						quantity--;
					}
				}

				/* adjust by one so it doesn't actually exceed limits */
				if (quantity > 0) {
					projectedDelta -= c.ourDelta;
					projectedGamma -= c.ourGamma;
					projectedVega -= c.ourVega;
					quantity--;
				}
				else if (quantity < 0) {
					projectedDelta += c.ourDelta;
					projectedGamma += c.ourGamma;
					projectedVega +=  c.ourVega;
					quantity++;
				}

				// add the values to the order map
				if (quantity != 0) {
					Double oldValue = orderMap.get(c.id);
					if (oldValue == null) 
						orderMap.put(c.id, quantity);
					else
						orderMap.put(c.id, oldValue + quantity);
				}
			}

			// remove any 0 orders in order map
			Iterator<Map.Entry<String, Double>> iter = orderMap.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, Double> entry = iter.next();
				if (entry.getValue().intValue() == 0)
					iter.remove();
				if (entry.getValue().intValue() < 0 && knownSymbols.get(entry.getKey()).price.bid < THRESHOLD) {
					log("removed selling at 0 issue");
					iter.remove();
				}
			}
 
			// create the set of orders based on how many objects are in the map
			ArrayList<OrderInfo> orderList = new ArrayList<OrderInfo>();
			for (Map.Entry<String, Double> entry : orderMap.entrySet()) {
				String symbol = entry.getKey();
				int quantity = entry.getValue().intValue();
				
				if (quantity > 0) {
					orderList.add(new OrderInfo(symbol, OrderSide.BUY, knownSymbols.get(symbol).price.ask, quantity));
					log("buy order being placed for " + symbol + " for quantity " + quantity + " at price " + knownSymbols.get(symbol).price.ask + ", gamma : " + knownSymbols.get(symbol).ourGamma);
				}
				else if (quantity < 0) {
					// don't bother clearing out long contracts that are worthless
					if (knownSymbols.get(symbol).price.bid < MIN_CONTRACT_BID_PRICE)
						continue;
					orderList.add(new OrderInfo(symbol, OrderSide.SELL, knownSymbols.get(symbol).price.bid, -1 * quantity));
					log("sell order being placed for " + symbol + " for quantity " + (-1 * quantity) + " at price " + knownSymbols.get(symbol).price.bid + ", gamma : " + knownSymbols.get(symbol).ourGamma);
				}
				else
					log("Error occurred in placing order");

			}
			OrderInfo[] orders = new OrderInfo[orderList.size()];
			orders = orderList.toArray(orders);

			log ("new projected Greeks are Delta : " + projectedDelta + ", Gamma : " + projectedGamma + ", Vega : " + projectedVega);

			return orders;
		}

		/* Update position counts for each option and risk positions */
		public void orderFilled(String idSymbol, double price, int quantity) {
			log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			Integer oldQuantity = positions.get(idSymbol);
			if (oldQuantity == null)
				log("Error in orderFilled(), oldQuantity is null!");
			Call c = knownSymbols.get(idSymbol);
			if (c == null)
				log("Error in orderFilled(), c is null!");

			positions.put(idSymbol, oldQuantity + quantity);
			capital -= price * quantity;

			if (quantity != 0) 
				calculateGreeks();
		}

		/* Update position counts and risk positions */
		public void penaltyFill(String idSymbol, double price, int quantity) {
			log("Penalty called...oh no! time : " + time);
			log("My penalty order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
			Integer oldQuantity = positions.get(idSymbol);
			if (oldQuantity == null)
				log("Error in penaltyFill(), oldQuantity is null!");
			Call c = knownSymbols.get(idSymbol);
			if (c == null)
				log("Error in penaltyFill(), c is null!");
			
			positions.put(idSymbol, oldQuantity + quantity);
			capital -= price * quantity;

			if (quantity != 0) 
				calculateGreeks();

			log("penalty greeks : Time = " + time + ", Delta = " + myDelta + ", Gamma = " + myGamma + ", Vega = " + myVega);
		}



		/* HELPER FUNCTIONS */

		private Boolean exceedingLimits() {
			return !((myDelta <= maxDelta) && (myDelta >= minDelta)
					&& (myGamma <= maxGamma) && (myGamma >= minGamma)
					&& (myVega <= maxVega) && (myVega >= minVega));

		}
		// clear out of all your positions now
		private OrderInfo[] clearPortfolio() {
			ArrayList<OrderInfo> orderList = new ArrayList<OrderInfo>();
			for (Map.Entry<String, Integer> entry : positions.entrySet()) {
				String symbol = entry.getKey();
				Integer position = entry.getValue();
				Call c = knownSymbols.get(symbol);
				if (c == null)
					log("Error in clearPortfolio()!");
				if (position > 0) {
					// no point selling worthless item anyways
					if (c.price.bid == 0.0)
						continue;
					orderList.add(new OrderInfo(symbol, OrderSide.SELL, c.price.bid, position));
					log("clearing sell order being placed for " + symbol + " for quantity " + position + " at price " + c.price.bid);
				}
				else if (position < 0) {
					orderList.add(new OrderInfo(symbol, OrderSide.BUY, c.price.ask, -1 * position));
					log("clearing buy order being placed for " + symbol + " for quantity " + (-1 * position) + " at price " + c.price.ask);
				}
			}

			OrderInfo[] orders = new OrderInfo[orderList.size()];
			orders = orderList.toArray(orders);
			log("clear Portfolio size is " + orderList.size());
			return orders;
		}

		// Use to clear some positions to get back within risk limits
		private OrderInfo[] adjustPortfolio() {
			ArrayList<OrderInfo> orderList = new ArrayList<OrderInfo>();

			for (Map.Entry<String, Integer> entry : positions.entrySet()) {
				String symbol = entry.getKey();
				Integer position = entry.getValue();
				Call c = knownSymbols.get(symbol);
				if (position == 0 || c.price.bid == 0)
					continue;

				double projectedDelta = myDelta;
				double projectedGamma = myGamma;
				double projectedVega = myVega;

				Integer count = 0;
				// while not within the limits, just remove elements
				while ( !((projectedDelta <= maxDelta) && (projectedDelta >= minDelta)
					&& (projectedGamma <= maxGamma) && (projectedGamma >= minGamma)
					&& (projectedVega <= maxVega) && (projectedVega >= minVega)) && (count < Math.abs(position))) 
				{
					if (position > 0) {
						projectedDelta -= c.ourDelta;
						projectedGamma -= c.ourGamma;
						projectedVega -= c.ourVega;
					}
					else {
						projectedDelta += c.ourDelta;
						projectedGamma += c.ourGamma;
						projectedVega += c.ourVega;
					}
					count++;
				}
				if (count != 0) {
					if (position > 0) {
						orderList.add(new OrderInfo(symbol, OrderSide.SELL, c.price.bid, count));
						log("adjusting sell order being placed for " + symbol + " for quantity " + count + " at price " + c.price.bid + "orig position is " + position);
					}
					else if (position < 0) {
						orderList.add(new OrderInfo(symbol, OrderSide.BUY, c.price.ask, count));
						log("adjusting buy order being placed for " + symbol + " for quantity " + count + " at price " + c.price.ask+ "orig position is " + position);
					}
				}
				log ("new adjusted projected Greeks are Delta : " + projectedDelta + ", Gamma : " + projectedGamma + ", Vega : " + projectedVega);

			}

			OrderInfo[] orders = new OrderInfo[orderList.size()];
			orders = orderList.toArray(orders);
			log("clearing some of portfolio to stay within risk limits!");
			return orders;
		}

		// calculate your current risk positions
		private void calculateGreeks() {
			double currentDelta = 0;
			double currentGamma = 0;
			double currentVega = 0;
			for (Map.Entry<String, Integer> entry : positions.entrySet()) {
				String symbol = entry.getKey();
				Integer position = entry.getValue();
				Call c = knownSymbols.get(symbol);
				if (c == null)
					log("something terrible has happed!");
				currentDelta += position * c.ourDelta;
				currentGamma += position * c.ourGamma;
				currentVega += position * c.ourVega;
			}
			myDelta = currentDelta;
			myGamma = currentGamma;
			myVega = currentVega;
		}

		// calculate your profit based on your current capital and the value of your portfolio
		private double calculateProfit() {
			double value = 0;
			for (Map.Entry<String, Integer> entry : positions.entrySet()) {
				String symbol = entry.getKey();
				Integer position = entry.getValue();
				Call c = knownSymbols.get(symbol);
				if (c == null)
					log("something terrible has happed!");
				if (c.valuesCalculated)
					value += position * c.price.getMidpoint();
			}
			return capital + value;
		}

		private Boolean isUnderlying(String idSymbol) {
			return (idSymbol.contains("E"));
		}

		// Parses symbol name to identify option maturity date or if it is the underlying
		private double getTimeToMaturity(String idSymbol) {
			if (idSymbol.contains("0527"))
				return FRONT_MONTH_TIME_TO_MATURITY;
			else if (idSymbol.contains("0627"))
				return BACK_MONTH_TIME_TO_MATURITY;
			else if (idSymbol.contains("E"))
				return 0;
			else {
				log("getTimeToMaturity() not an option " + idSymbol);
				return -1;
			}
		}		

		/* Parses symbol name to identify the strike price */
		private double getStrikePrice(String idSymbol) {
			if (idSymbol.contains("80C"))
				return 80;
			else if (idSymbol.contains("90C"))
				return 90;
			else if (idSymbol.contains("100C"))
				return 100;
			else if (idSymbol.contains("110C"))
				return 110;
			else if (idSymbol.contains("120C"))
				return 120;
			else 
				return 0;
		}

		// Identify the entry that is the underlying
		private double getSpotPrice() {
			for (Map.Entry<String, Call> entry : knownSymbols.entrySet()) {
				String s = entry.getKey();
				Call c = entry.getValue();
				if (isUnderlying(s)) {
					return c.price.getMidpoint();
				}
			}
			return -1; // error code
		}
		
		/* INNER CLASSES */

		/* Call Option Class */
		private final class Call {
			double expiryDate;
			double strikePrice;
			String id;
			Price price;

			double ourPrice = 0;
			double ourDelta = 0;
			double ourGamma = 0;
			double ourVega = 0;
			Boolean valuesCalculated = false;

			public Call(double date, double strike, String symbol, double bid, double ask) {
				expiryDate = date;
				strikePrice = strike;
				id = symbol;
				price = new Price(bid, ask);
			}

			public void setPrice(double bid, double ask) {
				price.bid = bid;
				price.ask = ask;
			}

			public Price getPrice() {
				return price;
			}

			public double getPercentPriceDifference() {
				return getPriceDifference() / ourPrice;
			}

			public double getPriceDifference() {
				return ourPrice - price.getMidpoint();
			}

			public boolean equals(Call c) {
				return (expiryDate == c.expiryDate && strikePrice == c.strikePrice);
			}
			
			@Override public String toString() {
				StringBuilder ans = new StringBuilder();
				ans.append(String.format("Call:(%.3f, %.3f) ", this.strikePrice, this.expiryDate));
				ans.append(price.toString());
				return ans.toString();
			}

			/* Price Class */
			private final class Price {
				double bid;
				double ask;

				public Price(double bid_, double ask_) {
					bid = bid_;
					ask = ask_;
				}
				public double getMidpoint() {
					return (bid + ask) / 2;
				}
				@Override public String toString() {
					//return "";
					return "[" + bid + ", " + ask + "]";
				}
			}

		}

	}

	@Override
	public OptionsCase getOptionCaseImplementation() {
		return new MySampleOptionImplementation();
	}

}
