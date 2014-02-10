package org.chicago.cases;

import java.util.List;

import org.chicago.cases.arb.ArbSignalProcessor;
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
		
		private int currentTick = 0;
		private Quote[] myQuotes;
		
		public void install(IJobSetup setup) {
			setup.addVariable("Team_Code", "Team Code and product to trade", "string", "");
			getArbCaseImplementation().addVariables(setup);
		}


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
			log("ArbCase implementation detected to be " + implementation.getClass().getSimpleName());
			
			teamDB = container.getDB(teamCode);
			implementation.initializeAlgo(teamDB);

		}
		
		/*
		* Called when the top of the book orders are updated
		
		public void onSignal(TopOfBookUpdate signal) {
			// Increment Tick
			this.tick++;
            log("Tick " + this.tick);

			// Send updates to algo
			log("Received TOB update");
			Quote[] quotes = new Quote[2];
			quotes[0] = signal.snowQuote;
			quotes[1] = signal.robotQuote;
			
			// Create a TOBUpdate event and add it to queue
            log("Creating TOB Update object");
			DelayedTopOfBook tobupdate = new DelayedTopOfBook(this.tick+5, quotes);
			this.queue.add(tobupdate);

			// Process market-crossing orders
            log("Processing market crossing orders");

            // No orders for first tick
            if (this.tick != 1) {
                for (int i = 0; i < 2; i++) {
                    int exchangeOrd = quotes[i].exchange.ordinal();

                    log("Processing exchange " + exchangeOrd);

                    if(this.algoQuotes[exchangeOrd].bidPrice > quotes[i].askPrice){
                        processOrder(AlgoSide.ALGOBUY,this.algoQuotes[i].bidPrice);
                    }
                    else if(this.algoQuotes[exchangeOrd].askPrice < quotes[i].bidPrice){
                        processOrder(AlgoSide.ALGOSELL,this.algoQuotes[i].askPrice);
                    }
                }
            }

			// Ask for new quotes every five ticks
            log("Check if new quotes from user.");
			if ((this.tick-1) % 5 == 0) {
				this.algoQuotes = implementation.refreshQuotes();
			}
			
		}


		public void onSignal(CustomerOrder signal) {

			// Process customer order
			log("Received CustomerOrder");

			int exchangeOrd = signal.exchange.ordinal();

			// If customer order crosses algo order, execute
			if(signal.side == CustomerSide.CUSTOMERBUY && signal.price > this.algoQuotes[exchangeOrd].askPrice){
				processOrder(AlgoSide.ALGOSELL, this.algoQuotes[exchangeOrd].askPrice);
			}
			else if(signal.side == CustomerSide.CUSTOMERSELL && signal.price < this.algoQuotes[exchangeOrd].bidPrice){
				processOrder(AlgoSide.ALGOBUY, this.algoQuotes[exchangeOrd].bidPrice);
			}
		}
		*/



}
