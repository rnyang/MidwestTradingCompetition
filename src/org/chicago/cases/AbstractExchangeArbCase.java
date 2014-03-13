package org.chicago.cases;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.chicago.cases.AbstractMathCase.TradeInfo;
import org.chicago.cases.AbstractOptionsCase.PositionInfo;
import org.chicago.cases.CommonSignals.EndSignal;
import org.chicago.cases.arb.ArbSignalProcessor;
import org.chicago.cases.arb.ArbSignals.CustomerOrder;
import org.chicago.cases.arb.ArbSignals.TopOfBookUpdate;
import org.chicago.cases.arb.ILatencyQueue;
import org.chicago.cases.arb.MapBasedLatencyManager;
import org.chicago.cases.arb.QueueEvent;
import org.chicago.cases.arb.QueueEvent.DelayedOrderFill;
import org.chicago.cases.arb.QueueEvent.DelayedTopOfBook;
import org.chicago.cases.arb.Quote;
import org.chicago.cases.options.OrderInfo.OrderSide;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IGrid;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.Prices;

public abstract class AbstractExchangeArbCase extends AbstractJob {
	
	private static final long STAT_REFRESH = 5000;
	private static final int DEFAULT_QUANTITY = 1;
	private static final int TOB_DELAY = 5;
	private static final int FILL_DELAY = 5;
	private static final int MAX_SHORT = -200;
	private static final int MAX_LONG = 200;
	
	private static final String STAT_GRID = "ARB";
	private static final String DATA_GRID = "ARB_DATA";
	private static final String MARKET_GRID = "ARB_MARKET";
	private static final String QUOTE_GRID = "ARB_QUOTES";
	DecimalFormat df = new DecimalFormat("##.##");
	
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
		
		class TradeInfo {
			
			final int position;
			final double price;
			
			private TradeInfo(int position, double price) {
				this.position = position;
				this.price = price;
			}
			
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
		protected int positionCount = 0;
		private int liquidationAmount = 0;
		private int liquidations = 0;
		private List<TradeInfo> trades = new ArrayList<TradeInfo>();
		private List<TradeInfo> penalties = new ArrayList<TradeInfo>();
		private IGrid statGrid;
		private IGrid marketGrid;
		private IGrid quoteGrid;
		private IGrid dataGrid;
		private int snowFills = 0;
		private int robotFills = 0;
		private String teamCode;
		private double tradeCount = 0;
		
		public void install(IJobSetup setup) {
			setup.addVariable("Team_Code", "Team Code and product to trade", "string", "");
			setup.addVariable("verbose", "verbose processing", "boolean", "false");
			setup.setVariable("timer", "" + STAT_REFRESH);
			getArbCaseImplementation().addVariables(setup);
		}


		@Override
		public void onTimer() {
			double pnl = calculatePNL(trades);
			double penaltyValue = calculatePNL(penalties);
			statGrid.set(teamCode, "pnl", formatNumber(pnl));
			statGrid.set(teamCode, "positions", formatNumber(positionCount));
			statGrid.set(teamCode, "liquidations", formatNumber(liquidations));
			statGrid.set(teamCode, "liquidatedAmount", formatNumber(liquidationAmount));
			statGrid.set(teamCode, "penaltyPnL", formatNumber(penaltyValue));
			statGrid.set(teamCode, "snowFills", formatNumber(snowFills));
			statGrid.set(teamCode, "robotFills", formatNumber(robotFills));
			statGrid.set(teamCode, "trades", formatNumber(tradeCount));
			snowFills = 0;
			robotFills = 0;
			
			for (Quote quote : currentMarketQuotes) {
				marketGrid.set(quote.exchange.name(), "bid", quote.bidPrice);
				marketGrid.set(quote.exchange.name(), "offer", quote.askPrice);
				dataGrid.set("bid", quote.exchange.name(), quote.bidPrice);
				dataGrid.set("offer", quote.exchange.name(), quote.askPrice);
			}
			
			for (Quote quote : myQuotes) {
				if (quote.exchange == Exchange.SNOW) {
					quoteGrid.set(teamCode, "snowBid", quote.bidPrice);
					quoteGrid.set(teamCode, "snowOffer", quote.askPrice);
				}
				else {
					quoteGrid.set(teamCode, "robotBid", quote.bidPrice);
					quoteGrid.set(teamCode, "robotOffer", quote.askPrice);	
				}
			}
			
		}


		private double formatNumber(double pnl) {
			return Double.parseDouble(df.format(pnl));
		}


		public void begin(IContainer container) {
			super.begin(container);
			
			statGrid = container.addGrid(STAT_GRID, new String[] {"pnl", "positions", "trades", "liquidations","liquidatedAmount", "penaltyPnL", "snowFills", "robotFills"});
			marketGrid = container.addGrid(MARKET_GRID, new String[] {"bid", "offer"});
			quoteGrid = container.addGrid(QUOTE_GRID, new String[] {"snowBid", "snowOffer", "robotBid", "robotOffer"}); 
			dataGrid = container.addGrid(DATA_GRID, new String[] {Exchange.SNOW.name(), Exchange.ROBOT.name()});
			
			verbose = getBooleanVar("verbose");
			teamCode = getStringVar("Team_Code");
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
			myInstrument = products.get(0) + "-E";
		
			container.subscribeToSignals();
			container.subscribeToMarketBidAskMessages();
			container.subscribeToTradeMessages();
			container.filterOnlyMyTrades(true);
			container.getPlaybackService().register(new ArbSignalProcessor());
			container.getPlaybackService().register(new CommonSignalProcessor());
			
			implementation = getArbCaseImplementation();
			log("ArbCase implementation detected to be " + implementation.getClass().getSimpleName());
			
			teamDB = container.getDB(teamCode);
			implementation.initializeAlgo(teamDB);

		}
		
		public void onSignal(EndSignal msg) {
			log("END signal received");
			double finalPNL = calculateFinalPNL();
			double pnl = calculatePNL();
			log("finalPNL=" + finalPNL + ", intraRound=" + pnl);
		}
		
		public void onSignal(TopOfBookUpdate signal) {
			internalLog("New TOB - Current tick is " + currentTick);
			
			// Add message to manager
			Quote[] quotes = new Quote[2];
			quotes[0] = signal.robotQuote;
			quotes[1] = signal.snowQuote;
			currentMarketQuotes = quotes;
			latencyManager.addQueueEvent(new DelayedTopOfBook(currentTick + TOB_DELAY, quotes));
			internalLog("Scheduled " + quotesToString(quotes) + " for tick " + (currentTick + TOB_DELAY));
			
			// Process delayed messages before potentially asking for new quotes
			processCurrentTick();
			
			if ((currentTick > 0) && ((currentTick % 5) == 0)) {
				Quote[] newQuotes = implementation.refreshQuotes();
				if (validQuotes(newQuotes))
					myQuotes = newQuotes;
				else
					log("Returned quotes are invalid.  They will not go to market");
				internalLog("Set my quotes to " + quotesToString(newQuotes));
			}
			
			// Process new TOB against new quotes
			internalLog("Processing TOB against my quotes");
			List<DelayedOrderFill> fills = matchAgainstTopOfBook(quotes);
			for (DelayedOrderFill fill : fills) {
				latencyManager.addQueueEvent(fill);
			}
			
			processPositions();
			
			currentTick += 1;
		}

		private boolean validQuotes(Quote[] quotes) {
			if (quotes == null || quotes.length != 2 || (quotes[0].exchange == quotes[1].exchange))
				return false;
			return true;
		}
		
		protected double calculateFinalPNL() {
			
			Prices snow = instruments().getAllPrices("SNOW-E");
			Prices robot = instruments().getAllPrices("ROBOT-E");
			double bestBid = Math.max(snow.bid, robot.bid);
			double bestAsk = Math.min(snow.ask, robot.ask);

			double pnl = 0;
			for (TradeInfo trade : trades) {
				double settlement = (trade.position > 0) ? bestBid : bestAsk;
				double cost = trade.position * trade.price;
				double value = trade.position * settlement;
				pnl += value - cost;
			}
			return pnl;
		}

		protected double calculatePNL() {
			return calculatePNL(trades);
		}

		private double calculatePNL(List<TradeInfo> tradeSource) {
			Prices snow = instruments().getAllPrices("SNOW-E");
			Prices robot = instruments().getAllPrices("ROBOT-E");
			double bidAverage = ((snow.bid + robot.bid) / 2);
			double askAverage = ((snow.ask + robot.ask) / 2);
			double settlement = ((bidAverage + askAverage) / 2);
			double pnl = 0;
			for (TradeInfo trade : tradeSource) {
				double cost = trade.position * trade.price;
				double value = trade.position * settlement;
				pnl += value - cost;
			}
			return pnl;
		}

		// Likely always liquidating quantity of 1
		// Refactor
		private void processPositions() {
			internalLog("current position is " + positionCount);
			if (positionCount > MAX_LONG) {
				int quantity = positionCount - MAX_LONG;
				double bestBid = Math.max(currentMarketQuotes[0].bidPrice, currentMarketQuotes[1].bidPrice);
				double penaltyPrice = bestBid * 0.8;
				positionCount -= quantity;
				long id = trades().manualTrade(myInstrument, quantity, penaltyPrice, com.optionscity.freeway.api.Order.Side.SELL, new Date(), null, null, null, null, null, null);
				implementation.positionPenalty(-quantity, penaltyPrice);
				trades.add(new TradeInfo(-quantity, penaltyPrice));
				penalties.add(new TradeInfo(-quantity, penaltyPrice));
				internalLog("liquidated " + -quantity + " @ " + penaltyPrice + " based on best bid of " + bestBid);
				liquidations += 1;
				liquidationAmount -= quantity;
			}
			else if (positionCount < MAX_SHORT) {
				int quantity = Math.abs(positionCount + MAX_LONG);
				double bestAsk = Math.min(currentMarketQuotes[0].askPrice, currentMarketQuotes[1].askPrice);
				double penaltyPrice = bestAsk * 1.2;
				positionCount += quantity;
				long id = trades().manualTrade(myInstrument, quantity, penaltyPrice, com.optionscity.freeway.api.Order.Side.BUY, new Date(), null, null, null, null, null, null);
				implementation.positionPenalty(quantity, penaltyPrice);
				trades.add(new TradeInfo(quantity, penaltyPrice));
				penalties.add(new TradeInfo(-quantity, penaltyPrice));
				internalLog("liquidated " + quantity + " @ " + penaltyPrice + " based on best ask of " + bestAsk);
				liquidations += 1;
				liquidationAmount += quantity;
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
			if (events == null)
				return;
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
			for (Quote quote : myQuotes) {
				for (Quote marketQuote : currentMarketQuotes) {
					if (quote.exchange == order.exchange && marketQuote.exchange == order.exchange) {
						if (order.side == CustomerSide.CUSTOMERBUY) {
							if (quote.askPrice > marketQuote.askPrice)
								return null;
						}
						else {
							if (quote.bidPrice < marketQuote.bidPrice)
								return null;
						}
					}
				}
			}
			return matchAgainstDetails(order.exchange, order.side, order.price);
		}
		
		private DelayedOrderFill matchAgainstDetails(Exchange exchange, CustomerSide side, double price) {
			for (Quote quote : myQuotes) {
				if (exchange == quote.exchange) {
					if (side == CustomerSide.CUSTOMERBUY) {
						internalLog("Matching bidPrice of " + price + " against askPrice of " + quote.askPrice + " for exchange " + exchange);
						if (price >= quote.askPrice) {
							internalLog("Successful match");
							positionCount -= DEFAULT_QUANTITY;
							long id = trades().manualTrade(myInstrument,
									 DEFAULT_QUANTITY,
									 quote.askPrice,
									 com.optionscity.freeway.api.Order.Side.SELL,
									 new Date(),
									 null, null, null, null, null, null);
							trades.add(new TradeInfo(-DEFAULT_QUANTITY, quote.askPrice));
							tradeCount += 1;
							if (exchange == Exchange.ROBOT)
								robotFills += 1;
							else
								snowFills += 1;
							return new DelayedOrderFill(currentTick + FILL_DELAY, AlgoSide.ALGOSELL, quote.askPrice, exchange);
							
						}
					}
					else {
						internalLog("Matching askPrice of " + price + " against bidPrice of " + quote.bidPrice);
						if (price <= quote.bidPrice) {
							internalLog("Successful match");
							positionCount += DEFAULT_QUANTITY;
							long id = trades().manualTrade(myInstrument,
									 DEFAULT_QUANTITY,
									 quote.bidPrice,
									 com.optionscity.freeway.api.Order.Side.BUY,
									 new Date(),
									 null, null, null, null, null, null);
							trades.add(new TradeInfo(DEFAULT_QUANTITY, quote.askPrice));
							tradeCount += 1;
							if (exchange == Exchange.ROBOT)
								robotFills += 1;
							else
								snowFills += 1;
							return new DelayedOrderFill(currentTick + FILL_DELAY, AlgoSide.ALGOBUY, quote.bidPrice, exchange);
						}
					}
				}
			}
			return null;
		}
		



}
