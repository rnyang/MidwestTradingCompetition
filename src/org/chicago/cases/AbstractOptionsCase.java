package org.chicago.cases;

import java.util.Date;
import java.util.List;

import org.chicago.cases.options.OptionSignalProcessor;
import org.chicago.cases.options.OptionSignals.AdminMessage;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
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
	
		private static final long ORDER_INTERVAL = 1000;
	
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
			
			void newAdminMessage(AdminMessage msg);
			
			void newForecastMessage(ForecastMessage msg);
			
			void newBidAsk(String idSymbol, double bid, double ask);
			
			OrderInfo[] placeOrders();
			
			void orderFilled(String idSymbol, double price, int quantity);
			
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
			setup.setVariable("timer", "" + ORDER_INTERVAL);
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
			if (getIntVar("timer") > ORDER_INTERVAL) {
				container.failJob("Timer has been modified!");
			}
			
			log("Team Code is, " + teamCode);
			
			List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.OPTIONS, teamCode);
			for (String product : products) {
				instruments().startSymbol(product);
				container.filterMarketMessages(product + ";;;;;;");
				log("filtering for " + product);
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
			log("Received TOB " + msg.instrumentId);
			Prices prices = instruments().getAllPrices(msg.instrumentId);
			implementation.newBidAsk(msg.instrumentId, prices.bid, prices.ask);
		}


		public void onSignal(AdminMessage msg) {
			log("Received new admin message");
			implementation.newAdminMessage(msg);
		}
		
		public void onSignal(ForecastMessage msg) {
			log("Received new forecast message");
			implementation.newForecastMessage(msg);
		}
		
		// Called at periodic interval - @See ORDER_INTERVAL
		public void onTimer() {
			OrderInfo[] orders = implementation.placeOrders();
			for (OrderInfo order : orders) {
				Order.Side side = (order.side == OrderSide.BUY) ? Order.Side.BUY : Order.Side.SELL;
				long id = trades().manualTrade(order.idSymbol,
						 order.quantity,
						 order.price,
						 side,
						 new Date(),
						 null, null, null, null, null, null);
				implementation.orderFilled(order.idSymbol, order.price, order.quantity);
			}
		}
}
