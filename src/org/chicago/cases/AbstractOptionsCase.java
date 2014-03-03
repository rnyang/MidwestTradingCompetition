package org.chicago.cases;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.chicago.cases.options.OptionSignalProcessor;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.OrderRequestMessage;
import org.chicago.cases.options.OptionSignals.ProcessPenaltyRequest;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.Order;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;

public abstract class AbstractOptionsCase extends AbstractJob {
	
		private static final long STAT_REFRESH = 1000;
		private static final double RATE = 0.01;
		
		private long position = 0;
		private RiskMessage currentLimits;
		private double currentUnderlying = 0;
		private String underlyingSymbol;
		private int currentTime = 0;
		
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
		
		@Override
		public void onMarketBidAsk(MarketBidAskMessage msg) {
			Prices prices = instruments().getAllPrices(msg.instrumentId);
			implementation.newBidAsk(msg.instrumentId, prices.bid, prices.ask);
			if (msg.instrumentId.equals(underlyingSymbol))
				currentTime += 1;
		}


		public void onSignal(RiskMessage msg) {
			implementation.newRiskMessage(msg);
		}
		
		public void onSignal(ForecastMessage msg) {
			implementation.newForecastMessage(msg);
		}
		
		public void onSignal(VolUpdate msg) {
			implementation.newVolUpdate(msg);
		}
		
		public void onSignal(ProcessPenaltyRequest msg) {
			double underlyingPrice = msg.underlyingPrice;
			if (underlyingPrice == Double.NaN) {
				Prices prices = instruments().getAllPrices(underlyingSymbol);
				underlyingPrice = (prices.ask + prices.bid) / 2;
			}
			PortfolioRisk risk = calculateRisk();
			if (risk.delta > currentLimits.maxDelta) {
				// liquidate delta
			}
			// liquidate vega and gamma
		}
		
		private PortfolioRisk calculateRisk() {
			// TODO Auto-generated method stub
			return null;
		}


		public void onSignal(OrderRequestMessage msg) {
			log("Received new order request message");
			OrderInfo[] orders = implementation.placeOrders();
			for (OrderInfo order : orders) {
				Order.Side side = (order.side == OrderSide.BUY) ? Order.Side.BUY : Order.Side.SELL;
				String idSymbol = order.idSymbol;
				Prices price = instruments().getAllPrices(idSymbol);
				double tradePrice = (order.side == OrderSide.BUY) ? price.ask : price.bid;
				int tradeQuantity = (order.side == OrderSide.BUY) ? order.quantity : -order.quantity;
				long id = trades().manualTrade(order.idSymbol,
						 order.quantity,
						 tradePrice,
						 side,
						 new Date(),
						 null, null, null, null, null, null);
				implementation.orderFilled(order.idSymbol, tradePrice, tradeQuantity);
				position += tradeQuantity;
				recordPosition(order.idSymbol, tradeQuantity);
				log("System filled order of side, " + order.side + " at price, " + tradePrice);
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
		
}
