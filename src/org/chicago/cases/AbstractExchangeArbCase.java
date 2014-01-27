package org.chicago.cases;

import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.lang.Math;
import java.util.Date;


import org.chicago.cases.arb.ArbSignalProcessor;
import org.chicago.cases.arb.ArbSignals.TopOfBookUpdate;
import org.chicago.cases.arb.ArbSignals.CustomerOrder;
import org.chicago.cases.arb.Quote;
import org.chicago.cases.arb.QueueEvent;
import org.chicago.cases.arb.QueueEvent.OrderFill;
import org.chicago.cases.arb.QueueEvent.TOBUpdate;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public abstract class AbstractExchangeArbCase extends AbstractJob {
	
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
			
			void initialize(Quote[] startingQuotes);
			
			void fillNotice(Exchange exchange, double price);
			
			void positionPenalty(int clearedQuantity, double price);
			
			void newTopOfBook(Quote[] quotes);
			
			Quote[] refreshQuotes();
			
		}
		
		// STATE VARIABLES
		private int tick;
		private Quote[] algoQuotes;
		private int pos;
		private Queue<QueueEvent> queue;
		private Quote[] latestTOB;

		// ----------- Handle System Events and Translate to Case Interface Methods ---------------
		
		private IDB teamDB;
		private ArbCase implementation;
		
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
			getArbCaseImplementation().addVariables(setup);
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
		
			container.subscribeToSignals();
			container.subscribeToMarketBidAskMessages();
			container.subscribeToTradeMessages();
			container.filterOnlyMyTrades(true);
			container.getPlaybackService().register(new ArbSignalProcessor());
			
			implementation = getArbCaseImplementation();
			log("MathCase implementation detected to be " + implementation.getClass().getSimpleName());
			
			teamDB = container.getDB(teamCode);
			implementation.initializeAlgo(teamDB);


			// My code
			tick = 0;
			Queue<QueueEvent> queue = new LinkedList<QueueEvent>();
		}

		
		public void onSignal(TopOfBookUpdate signal) {
			// Next Tick
			tick++;

			// Send market updates to algo (lagged)
			log("Received TOB update");
			Quote[] quotes = new Quote[2];
			quotes[0] = signal.snowQuote;
			quotes[1] = signal.robotQuote;
			TOBUpdate tobupdate = TOBUpdate(this.tick+5, quotes);
			this.queue.add(tobupdate);

			// Process market-crossing orders
			for (int i= 0;i<2;i++) {
				if(this.algoQuotes[quotes[i].exchange.ordinal()].bidPrice > quotes[i].askPrice){
					processOrder(AlgoSide.ALGOBUY,this.algoQuotes[i].bidPrice);
				}else if(this.algoQuotes[quotes[i].exchange.ordinal()].askPrice < quotes[i].bidPrice){
					processOrder(AlgoSide.ALGOSELL,this.algoQuotes[i].askPrice);
				}
			}

			// Ask for new quotes
			if(tick%5 == 0){
				this.algoQuotes = implementation.refreshQuotes();
			}
		}

		public void onSignal(CustomerOrder signal) {
			log("Received CustomerOrder");
			if(signal.side == CustomerSide.CUSTOMERBUY){
				if(signal.price > this.algoQuotes[signal.exchange.ordinal()].askPrice){
					processOrder(AlgoSide.ALGOSELL,this.algoQuotes[signal.exchange.ordinal()].askPrice);
				}
			}else if(signal.side == CustomerSide.CUSTOMERSELL){
				if(signal.price < this.algoQuotes[signal.exchange.ordinal()].bidPrice){
					processOrder(AlgoSide.ALGOBUY,this.algoQuotes[signal.exchange.ordinal()].bidPrice);
				}
			}
		}

		public void processOrder(AlgoSide side, double price){
			if(side == AlgoSide.ALGOBUY){
				long id = trades().manualTrade(1234567,				// Instruments PLEASE HELP
					 1,
					 price,
					 com.optionscity.freeway.api.Order.Side.BUY,
					 new Date(),
					 null, null, null, null, null, null);
				pos += 1;
			}else if(side == AlgoSide.ALGOSELL){
				long id = trades().manualTrade(1234567,				// Instruments PLEASE HELP
					 1,
					 price,
					 com.optionscity.freeway.api.Order.Side.SELL,
					 new Date(),
					 null, null, null, null, null, null);
				pos += 1;
			}

			checkPenalty();
		}

		public void checkPenalty(){
			if(this.pos > 200){
				long id = trades().manualTrade(1234567,				// Instruments PLEASE HELP
					 this.pos-200,
					 Math.min(this.latestTOB[0].bidPrice,this.latestTOB[1].bidPrice)*0.8,
					 com.optionscity.freeway.api.Order.Side.SELL,
					 new Date(),
					 null, null, null, null, null, null);
				this.pos = 200;
			}
			if(this.pos < -200){
				long id = trades().manualTrade(SOMEINSTRUMENT,				// Instruments PLEASE HELP
					 -200-this.pos,
					 Math.min(this.latestTOB[0].askPrice,this.latestTOB[1].askPrice)*1.2,
					 com.optionscity.freeway.api.Order.Side.BUY,
					 new Date(),
					 null, null, null, null, null, null);
				this.pos = -200;
			}
		}		

		public void processQueue(int tick){
			while(!this.queue.isEmpty() && this.queue.peek().tick >= tick){
				QueueEvent event = this.queue.poll();
				if(event instanceof OrderFill){
					implementation.fillNotice(event.exchange, event.price);
				}else if(event instanceof TOBUpdate){
					implementation.newTopOfBook(event.quotes);
				}
			}
		}
}
