package org.chicago.cases;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.chicago.cases.arb.ArbSignalProcessor;
import org.chicago.cases.arb.ArbSignals.CustomerOrder;
import org.chicago.cases.arb.ArbSignals.TopOfBookUpdate;
import org.chicago.cases.arb.ILatencyQueue;
import org.chicago.cases.arb.MapBasedLatencyManager;
import org.chicago.cases.arb.QueueEvent;
import org.chicago.cases.arb.QueueEvent.DelayedOrderFill;
import org.chicago.cases.arb.QueueEvent.DelayedTopOfBook;
import org.chicago.cases.arb.Quote;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public abstract class AbstractExchangeArbCase extends AbstractJob {
	
	private static final long STAT_REFRESH = 1000;
	private static final int DEFAULT_QUANTITY = 1;
	private static final int TOB_DELAY = 5;
	private static final int FILL_DELAY = 5;
	private static final int MAX_SHORT = 200;
	private static final int MAX_LONG = 200;
	
	// ---------------- Define Case Interface and abstract method ----------------
		/*
		 * By having the abstract method: getArbCaseImplementation(), we are saying, "All classes that
		 * extend AbstractMathCase must return their implementation of the MathCase interface".
		 * 
		 * 
		 */
		public abstract ArbCase getArbCaseImplementation();
		
		public static enum Exchange {
			SNOW, ROBOT
		}
		
		public static enum CustomerSide {
			CUSTOMERBUY, CUSTOMERSELL
		}

		public static enum AlgoSide {
			ALGOBUY, ALGOSELL
		}

		public static interface ArbCase {
			
			void addVariables(IJobSetup setup);
			
			void initializeAlgo(IDB dataBase);
			
			void fillNotice(Exchange exchange, double price, AlgoSide algoside);
			
			void positionPenalty(int clearedQuantity, double price);
			
			void newTopOfBook(Quote[] quotes);
			
			Quote[] refreshQuotes();
			
		}
		
		// ------------------ Begin Impl -----------------------------

		
		private IDB teamDB;
		private ArbCase implementation;
		private ILatencyQueue latencyManager = new MapBasedLatencyManager();
		
		private String myInstrument;
		private int currentTick = 0;
		private Quote[] myQuotes = new Quote[0];
		private Quote[] currentMarketQuotes = new Quote[0];
		private boolean verbose = false;
		private int positionCount = 0;
		
		public void install(IJobSetup setup) {
			setup.addVariable("Team_Code", "Team Code and product to trade", "string", "");
			setup.addVariable("verbose", "verbose processing", "boolean", "false");
			getArbCaseImplementation().addVariables(setup);
		}


		public void begin(IContainer container) {
			super.begin(container);
			verbose = getBooleanVar("verbose");
			String teamCode = getStringVar("Team_Code");
			if (teamCode.isEmpty())
				container.stopJob("Please set a Team_Code in the configuration");
			if (!TeamUtilities.validateTeamCode(teamCode))
				container.stopJob("The specified Team Code is not a valid code.  Please enter the code provided to your team.");
			
			log("Team Code is, " + teamCode);
			
			List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.ARB, teamCode);
			for (String product : products) {
				instruments().startSymbol(product);
				container.filterMarketMessages(product + ";;;;;;");
				log("filtering for " + product);
			}
			myInstrument = products.get(0);
		
			container.subscribeToSignals();
			container.subscribeToMarketBidAskMessages();
			container.subscribeToTradeMessages();
			container.filterOnlyMyTrades(true);
			container.getPlaybackService().register(new ArbSignalProcessor());
			
			implementation = getArbCaseImplementation();
			log("ArbCase implementation detected to be " + implementation.getClass().getSimpleName());
			
			teamDB = container.getDB(teamCode);
			implementation.initializeAlgo(teamDB);

		}
		
		public void onSignal(TopOfBookUpdate signal) {
			internalLog("New TOB - Current tick is " + currentTick);
			currentTick += 1;
			
			// Add message to manager
			Quote[] quotes = new Quote[2];
			quotes[0] = signal.robotQuote;
			quotes[1] = signal.snowQuote;
			currentMarketQuotes = quotes;
			latencyManager.addQueueEvent(new DelayedTopOfBook(currentTick + TOB_DELAY, quotes));
			internalLog("Scheduled " + quotesToString(quotes) + " for tick " + currentTick + TOB_DELAY);
			
			// Process delayed messages before potentially asking for new quotes
			processCurrentTick();
			
			if ((currentTick % 5) == 0) {
				myQuotes = implementation.refreshQuotes();
				internalLog("Set my quotes to " + quotesToString(quotes));
			}
			
			// Process new TOB against new quotes
			internalLog("Processing TOB against my quotes");
			List<DelayedOrderFill> fills = matchAgainstTopOfBook(quotes);
			for (DelayedOrderFill fill : fills) {
				latencyManager.addQueueEvent(fill);
			}
			
			processPositions();
		}


		// Likely always liquidating quantity of 1
		private void processPositions() {
			internalLog("current position is " + positionCount);
			if (positionCount > MAX_LONG) {
				
				int quantity = positionCount - MAX_LONG;
				double bestBid = Math.max(currentMarketQuotes[0].bidPrice, currentMarketQuotes[1].bidPrice);
				double penaltyPrice = bestBid * 0.8;
				positionCount -= quantity;
				long id = trades().manualTrade(myInstrument, quantity, penaltyPrice, com.optionscity.freeway.api.Order.Side.SELL, new Date(), null, null, null, null, null, null);
				implementation.positionPenalty(quantity, penaltyPrice);
				internalLog("liquidated " + quantity + " @ " + penaltyPrice + " based on best bid of " + bestBid);
			}
			else if (positionCount < MAX_SHORT) {
				int quantity = Math.abs(positionCount + MAX_LONG);
				double bestAsk = Math.min(currentMarketQuotes[0].askPrice, currentMarketQuotes[1].askPrice);
				double penaltyPrice = bestAsk * 1.2;
				positionCount += quantity;
				long id = trades().manualTrade(myInstrument, quantity, penaltyPrice, com.optionscity.freeway.api.Order.Side.BUY, new Date(), null, null, null, null, null, null);
				implementation.positionPenalty(-quantity, penaltyPrice);
				internalLog("liquidated " + -quantity + " @ " + penaltyPrice + " based on best ask of " + bestAsk);
			}
		}


		private void internalLog(String message) {
			if (verbose)
				log(message);
		}


		private String quotesToString(Quote[] quotes) {
			return "{" + quotes[0].exchange.toString().charAt(0) + ":" + quotes[0].bidPrice + "@" + quotes[0].askPrice + ", " + quotes[1].exchange.toString().charAt(0) + ":" + quotes[1].bidPrice + "@" + quotes[1].askPrice + "}";
		}
		
		private String orderToString(CustomerOrder order) {
			return "exchange=" + order.exchange + ", side=" + order.side + ", price=" + order.price;
		}


		private void processCurrentTick() {
			
			List<QueueEvent> events = latencyManager.getAllEventsForTick(currentTick);
			for (QueueEvent event : events) {
				internalLog("Delivering delayed event " + event.getClass().getSimpleName() + " at tick " + currentTick);
				if (event instanceof DelayedTopOfBook) {
					DelayedTopOfBook tob = (DelayedTopOfBook)event;
					implementation.newTopOfBook(tob.quotes);
				}
				else if (event instanceof DelayedOrderFill) {
					DelayedOrderFill order = (DelayedOrderFill)event;
					implementation.fillNotice(order.exchange, order.price, order.algoside);
				}
				else {
					log("Skipping unkown event, " + event.getClass().getName());
				}
			}
		}


		public void onSignal(CustomerOrder signal) {
			internalLog("New customer order " + orderToString(signal));
			DelayedOrderFill fill = matchAgainstOrder(signal);
			if (fill != null)
				latencyManager.addQueueEvent(fill);
			processPositions();
		}
		
		


		private List<DelayedOrderFill> matchAgainstTopOfBook(Quote[] tob) {
			List<DelayedOrderFill> fills = new ArrayList<DelayedOrderFill>();
			for (Quote quote : tob) {
				DelayedOrderFill bidFill = matchAgainstDetails(quote.exchange, CustomerSide.CUSTOMERBUY, quote.bidPrice);
				if (bidFill != null)
					fills.add(bidFill);
				DelayedOrderFill askFill = matchAgainstDetails(quote.exchange, CustomerSide.CUSTOMERSELL, quote.askPrice);
				if (askFill != null)
					fills.add(askFill);
			}
			return fills;
		}
		
		private DelayedOrderFill matchAgainstOrder(CustomerOrder order) {
			return matchAgainstDetails(order.exchange, order.side, order.price);
		}
		
		private DelayedOrderFill matchAgainstDetails(Exchange exchange, CustomerSide side, double price) {
			for (Quote quote : myQuotes) {
				if (exchange == quote.exchange) {
					if (side == CustomerSide.CUSTOMERBUY) {
						internalLog("Matching bidPrice of " + price + " against askPrice of " + quote.askPrice);
						if (price >= quote.askPrice) {
							internalLog("Successful match");
							positionCount += 1;
							long id = trades().manualTrade(myInstrument,
									 DEFAULT_QUANTITY,
									 quote.askPrice,
									 com.optionscity.freeway.api.Order.Side.BUY,
									 new Date(),
									 null, null, null, null, null, null);
							return new DelayedOrderFill(currentTick + FILL_DELAY, AlgoSide.ALGOBUY, quote.askPrice, exchange);
						}
					}
					else {
						internalLog("Matching bidPrice of " + price + " against askPrice of " + quote.askPrice);
						if (price <= quote.bidPrice) {
							internalLog("Successful match");
							positionCount -= 1;
							long id = trades().manualTrade(myInstrument,
									 DEFAULT_QUANTITY,
									 quote.bidPrice,
									 com.optionscity.freeway.api.Order.Side.SELL,
									 new Date(),
									 null, null, null, null, null, null);
							return new DelayedOrderFill(currentTick + FILL_DELAY, AlgoSide.ALGOSELL, quote.bidPrice, exchange);
						}
					}
				}
			}
			return null;
		}
		



}
