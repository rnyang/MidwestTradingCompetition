package org.chicago.cases;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.chicago.cases.options.OptionSignalProcessor;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.OrderRequestMessage;
import org.chicago.cases.options.OptionSignals.ProcessPenaltyRequest;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;
import com.optionscity.freeway.api.Order;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;

public abstract class AbstractOptionsCase extends AbstractJob {
	
		private static final long STAT_REFRESH = 1000;
		private static final double RATE = 0.01;
		private static final int DAYS_PER_ROUND = 100;
		
		private RiskMessage currentLimits;
		private double currentUnderlying = 0;
		private String underlyingSymbol;
		private int currentTime = 0;
		private int daysToJuneExp = 100;
		private int daysToMayExp = 130;
		private double currentVol = 0;
		
		private Random random = new Random();
		private List<String> optionList = new ArrayList<String>();
		private Map<String, Integer> positionMap = new ConcurrentHashMap<String, Integer>();
		
		class PortfolioRisk {
			
			private final double delta;
			private final double gamma;
			private final double vega;
			
			private PortfolioRisk(double delta, double gamma, double vega) {
				this.delta = delta;
				this.gamma = gamma;
				this.vega = vega;
			}
			
		}
		
	
		// ---------------- Define Case Interface and abstract method ----------------
		/*
		 * By having the abstract method: getOptionsCaseImplementation(), we are saying, "All classes that
		 * extend AbstractMathCase must return their implementation of the MathCase interface".
		 * 
		 * When the algo (we call algos "jobs") is started, the system will call getMathCaseImplementation() and
		 * get the team's implementation.  It then has a reference to their implementation so that in can
		 * call the corresponding methods as events happen.  This will make sense when you look at the Sample implementation.
		 */
		public abstract OptionsCase getOptionCaseImplementation();
		
		public static interface OptionsCase {
			
			void addVariables(IJobSetup setup);
			
			void initializeAlgo(IDB dataBase);
			
			void newRiskMessage(RiskMessage msg);
			
			void newForecastMessage(ForecastMessage msg);
			
			void newVolUpdate(VolUpdate msg);
			
			void newBidAsk(String idSymbol, double bid, double ask);
			
			OrderInfo[] placeOrders();
			
			void orderFilled(String idSymbol, double price, int quantity);
			
			void penaltyFill(String idSymbol, double price, int quantity);
			
		}
		
		

		
		// ----------- Handle System Events and Translate to Case Interface Methods ---------------
		
		private IDB teamDB;
		private OptionsCase implementation;
		private boolean verbose = false;
		
		
		/*
		 * Freeway has its own events that are likely too complex for the student's to work out in one month.
		 * So what we do here is translate from the system events in Freeway, to the interface methods we've defined above.
		 * 
		 * Because of this, this class, "AbstractMathCase" now has control of the flow and we are basically acting as a
		 * middle-man between the system and the team's implementation.  Thus, we get to do specialized things, like
		 * assume infinite liquidity, add new risk penalties, etc. that otherwise wouldn't exist in the system.
		 */
		
		/*
		 * Required freeway method.  The IJobSetup object is used to register variables
		 * for this particular job
		 */
		@Override
		public void install(IJobSetup setup) {
			setup.addVariable("Team_Code", "Team Code and product to trade", "string", "");
			setup.addVariable("verbose", "verbose processing", "boolean", "false");
			getOptionCaseImplementation().addVariables(setup);
		}


		/*
		 * Begin() is called when the job is started.  I've wanted to hide this from them because it gives them
		 * access to the container which would allow them to mess w/ system settings.
		 */
		@Override
		public void begin(IContainer container) {
			super.begin(container);
			
			String teamCode = getStringVar("Team_Code");
			verbose = getBooleanVar("verbose");
			if (teamCode.isEmpty())
				container.failJob("Please set a Team_Code in the configuration");
			if (!TeamUtilities.validateTeamCode(teamCode))
				container.failJob("The specified Team Code is not a valid code.  Please enter the code provided to your team.");
			
			log("Team Code is, " + teamCode);
			
			List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.OPTIONS, teamCode);
			for (String product : products) {
				instruments().startSymbol(product);
				container.filterMarketMessages(product + ";;;;;;");
				log("filtering for " + product);
				positionMap.put(product, 0);
				if (product.contains("-E")) {
					underlyingSymbol = product;
					log("underlying product is " + underlyingSymbol);
				}
				else {
					optionList.add(product);
				}
			}
		
			
			container.subscribeToMarketBidAskMessages();
			container.subscribeToTradeMessages();
			container.subscribeToSignals();
			container.filterOnlyMyTrades(true);
			container.getPlaybackService().register(new OptionSignalProcessor());
			
			implementation = getOptionCaseImplementation();
			log("MathCase implementation detected to be " + implementation.getClass().getSimpleName());
			
			teamDB = container.getDB(teamCode);
			implementation.initializeAlgo(teamDB);
		}
		
		public void onMarketBidAsk(MarketBidAskMessage msg) {
			Prices prices = instruments().getAllPrices(msg.instrumentId);
			implementation.newBidAsk(msg.instrumentId, prices.bid, prices.ask);
			if (msg.instrumentId.equals(underlyingSymbol)) {
				updateTimeData();
				currentUnderlying = (prices.ask + prices.bid) / 2; // midpoint
			}
		}

		public void onSignal(RiskMessage msg) {
			implementation.newRiskMessage(msg);
		}
		
		public void onSignal(ForecastMessage msg) {
			implementation.newForecastMessage(msg);
		}
		
		public void onSignal(VolUpdate msg) {
			implementation.newVolUpdate(msg);
			currentVol = msg.impliedVol;
		}
		
		public void onSignal(ProcessPenaltyRequest msg) {
			if (currentVol == 0) {
				log("Volatility is currently not set.  Penalties will not be processed");
				return;
			}
			
			// Get underlying price to use
			double underlyingPrice = msg.underlyingPrice;
			if (underlyingPrice == Double.NaN) {
				underlyingPrice = currentUnderlying;
			}
			
			// Get risk from current data
			PortfolioRisk risk = calculateRisk(underlyingPrice, RATE, currentVol);
			
			// Get instrument to offset with
			String option = getFrontMonthATM();
			internalLog("using " + option + " to reduce gamma and vega risk");
			
			Prices optionPrices = instruments().getAllPrices(option);
			InstrumentDetails details = instruments().getInstrumentDetails(option);
			double vega = Optionsutil.calculateVega(underlyingPrice, details.strikePrice, daysToMayExp, RATE, currentVol);
			double gamma = Optionsutil.calculateGamma(underlyingPrice, details.strikePrice, daysToMayExp, RATE, currentVol);
			double delta = Optionsutil.calculateDelta(underlyingPrice, details.strikePrice, daysToMayExp, RATE, currentVol);
			internalLog("ask= " + optionPrices.ask + ", bid=" + optionPrices.bid + ", v=" + vega + ", g=" + gamma + ", d=" + delta);
			
			
			// Handle vega or gamma
			
			double vegaNeeded = 0;
			double gammaNeeded = 0;
			double tradeQuantity = 0;
			double deltaOffset = 0;
			internalLog("running penalty liquidation logic");
			internalLog("current limits are, mng=" + currentLimits.minGamma + ", mxg=" + currentLimits.minGamma + ", mnv=" + currentLimits.minVega + ", mxv=" + currentLimits.maxVega + ", mnd=" + currentLimits.minDelta + ", mxd=" + currentLimits.maxDelta);
			internalLog("risk is, g=" + risk.gamma + ", v=" + risk.vega + ", d=" + risk.delta);
			if (risk.gamma > currentLimits.maxGamma || risk.gamma < currentLimits.minGamma) {
				gammaNeeded = (risk.gamma > currentLimits.maxGamma) ? currentLimits.maxGamma - risk.gamma : currentLimits.minGamma - risk.gamma;
			}
			if (risk.vega > currentLimits.maxVega || risk.vega < currentLimits.minVega) {
				gammaNeeded = (risk.vega > currentLimits.maxVega) ? currentLimits.maxVega - risk.vega : currentLimits.minVega - risk.vega;
			}
			
			if (gammaNeeded != 0 || vegaNeeded != 0) {
				// hedge required, choose a weapon
				
				boolean useGamma = Math.abs(gammaNeeded) > Math.abs(vegaNeeded);
				if (useGamma) {
					tradeQuantity = gammaNeeded / gamma;
					internalLog("using gamma to hedge, needed=" + gammaNeeded + ", gamma=" + gamma + " qty=" + tradeQuantity);
				}
				else {
					tradeQuantity = vegaNeeded / vega;
					internalLog("using vega to hedge, needed=" + vegaNeeded + ", vega=" + vega + " qty=" + tradeQuantity);
				}
				
				// Make trade
				double priceToLiquidate = (tradeQuantity > 0) ? optionPrices.ask * 1.05 : optionPrices.bid * 0.95;
				int roundedTradeQuantity = (int)((tradeQuantity > 0) ? Math.ceil(tradeQuantity) : Math.floor(tradeQuantity));		
				Order.Side side = (tradeQuantity > 0) ? Order.Side.BUY : Order.Side.SELL;
				long id = trades().manualTrade(option, roundedTradeQuantity, priceToLiquidate, side, new Date(), null, null, null, null, null, null);
				
				deltaOffset += roundedTradeQuantity * delta;
				recordPosition(option, roundedTradeQuantity);
				internalLog("liquidated " + roundedTradeQuantity + " @ " + priceToLiquidate);
				implementation.penaltyFill(option, priceToLiquidate, roundedTradeQuantity);
			}
			else {
				internalLog("No gamma or vega liquidation necessary");
			}
			
			
			
			// Liquidate delta
			double currentDelta = risk.delta + deltaOffset;
			internalLog("using " + currentDelta + " for delta risk limits");
			if (currentDelta > currentLimits.maxDelta || currentDelta < currentLimits.minDelta) {
				
				double deltaNeeded = (risk.delta > currentLimits.maxDelta) ? currentLimits.maxDelta - risk.delta : currentLimits.minDelta - risk.delta;
				tradeQuantity = deltaNeeded / delta;
				internalLog("need to liquidate some delta, deltaNeeded=" + deltaNeeded + ", delta=" + delta + ", qty=" + tradeQuantity);
				
				Prices prices = instruments().getAllPrices(underlyingSymbol);
				double priceToLiquidate = (deltaNeeded > 0) ? prices.ask * 1.05 : prices.bid * 0.95;
				int tradeQuantityRounded = (int)((deltaNeeded > 0) ? Math.ceil(deltaNeeded) : Math.floor(deltaNeeded));
				Order.Side side = (deltaNeeded > 0) ? Order.Side.BUY : Order.Side.SELL;
				long id = trades().manualTrade(underlyingSymbol, tradeQuantityRounded, priceToLiquidate, side, new Date(), null, null, null, null, null, null);
				
				recordPosition(underlyingSymbol, tradeQuantityRounded);
				internalLog("liquidated " + tradeQuantityRounded + " @ " + priceToLiquidate);
				implementation.penaltyFill(underlyingSymbol, priceToLiquidate, tradeQuantityRounded);
			}
			else {
				internalLog("No delta liquidation necessary");
			}
			internalLog("penalty liquidation complete");
		}
		
		private String getFrontMonthATM() {
			Calendar cal = Calendar.getInstance();
			double closestPrice = Double.NaN;
			String chosenOption = null;
			for (String option : optionList) {
				InstrumentDetails details = instruments().getInstrumentDetails(option);
				cal.setTime(details.expiration);
				if (cal.get(Calendar.MONTH) == Calendar.MAY) {
					Prices prices = instruments().getAllPrices(option);
					double midPoint = (prices.bid + prices.ask) / 2;
					double distance = currentUnderlying - midPoint;
					if ((closestPrice == Double.NaN) || (Math.abs(distance) < Math.abs(closestPrice))) {
						chosenOption = option;
						closestPrice = distance;
					}
				}
			}
			return chosenOption;
		}


		private void updateTimeData() {
			currentTime += 1;
			daysToJuneExp -= 1;
			daysToMayExp -= 1;
			if (currentTime > DAYS_PER_ROUND) {
				internalLog("DAYS EXCEEDED ROUND MAXIMUM(?)");
			}
		}
		
		private PortfolioRisk calculateRisk(double spot, double interestRate, double vol) {
			internalLog("calculating risk, spot=" + spot + ", currentTime=" + currentTime + ", vol=" + vol + ", rate=" + interestRate);
			
			Calendar cal = Calendar.getInstance();
			
			double delta = 0;
			double vega = 0;
			double gamma = 0;
			int totalPosition = 0;
			
			// Set delta to current underlying position count
			int underlyingPosition = positionMap.get(underlyingSymbol);
			delta += underlyingPosition;
			internalLog("underlying, pos=" + underlyingPosition + ", delta=" + delta);
			
			// Aggregate greeks for each options
			for (String option : optionList) {
				// Get instrument details
				int position = positionMap.get(option);
				InstrumentDetails detail = instruments().getInstrumentDetails(option);
				cal.setTime(detail.expiration);
				int monthCode = cal.get(Calendar.MONTH);
				int days = (monthCode == Calendar.MAY) ? daysToMayExp : daysToJuneExp;
				internalLog("using daysToExpiration= " + days +", for expiration of " + detail.displayExpiration);
				
				// Calculate and add risk
				double instrumentVega = Optionsutil.calculateVega(spot, detail.strikePrice, days, interestRate, vol);
				double instrumentDelta = Optionsutil.calculateDelta(spot, detail.strikePrice, days, interestRate, vol);
				double instrumentGamma = Optionsutil.calculateGamma(spot, detail.strikePrice, days, interestRate, vol);
				internalLog("adding risk for " + option + ", pos=" + position + ", v=" + instrumentVega + ", g=" + instrumentGamma + ", d=" + instrumentDelta);
				vega += position * instrumentVega;
				gamma += position * instrumentGamma;
				delta += position * instrumentDelta;
				totalPosition += position;
			}
			internalLog("current risk is , pos=" + totalPosition + ", v=" + vega + ", g=" + gamma + ", d=" + delta);
			return new PortfolioRisk(delta, gamma, vega);
		}


		public void onSignal(OrderRequestMessage msg) {
			internalLog("Received new order request message");
			OrderInfo[] orders = implementation.placeOrders();
			
			for (OrderInfo order : orders) {
				// Get details
				Order.Side side = (order.side == OrderSide.BUY) ? Order.Side.BUY : Order.Side.SELL;
				String idSymbol = order.idSymbol;
				Prices price = instruments().getAllPrices(idSymbol);
				double tradePrice = (order.side == OrderSide.BUY) ? price.ask : price.bid;
				int tradeQuantity = (order.side == OrderSide.BUY) ? order.quantity : -order.quantity;
				
				// Make Trade
				long id = trades().manualTrade(order.idSymbol, order.quantity, tradePrice, side, new Date(), null, null, null, null, null, null);
				implementation.orderFilled(order.idSymbol, tradePrice, tradeQuantity);
				
				// Update position
				recordPosition(order.idSymbol, tradeQuantity);
			}
		}


		private void recordPosition(String idSymbol, int tradeQuantity) {
			int currentPosition = 0;
			if (positionMap.containsKey(idSymbol)) {
				currentPosition = positionMap.get(idSymbol);
			}
			currentPosition += tradeQuantity;
			positionMap.put(idSymbol, currentPosition);
		}
		
		private void internalLog(String message) {
			if (verbose)
				log(message);
		}
		
}
