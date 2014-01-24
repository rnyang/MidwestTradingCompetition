package org.chicago.cases;

import java.util.Date;
import java.util.List;

import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.TeamUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.OrderRequest;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;

public abstract class AbstractMathCase extends AbstractJob {
	
	// ---------------- Define Case Interface and abstract method ----------------
	/*
	 * By having the abstract method: getMathCaseImplementation(), we are saying, "All classes that
	 * extend AbstractMathCase must return their implementation of the MathCase interface".
	 * 
	 * When the algo (we call algos "jobs") is started, the system will call getMathCaseImplementation() and
	 * get the team's implementation.  It then has a reference to their implementation so that in can
	 * call the corresponding methods as events happen.  This will make sense when you look at the Sample implementation.
	 */
	public abstract MathCase getMathCaseImplementation();
	
	public static interface MathCase {
		
		void addVariables(IJobSetup setup);
		
		void initializeAlgo(IDB dataBase);
		
		int newBidAsk(double bid, double ask);
		
		void orderFilled(int volume, double fillPrice);
		
	}
	
	// ----------- Handle System Events and Translate to Case Interface Methods ---------------
	
	private IDB teamDB;
	private MathCase implementation;
	
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
		getMathCaseImplementation().addVariables(setup);
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
		
		List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.MATH, teamCode);
		for (String product : products) {
			instruments().startSymbol(product);
			container.filterMarketMessages(product + ";;;;;;");
			log("filtering for " + product);
		}
	
		
		container.subscribeToMarketBidAskMessages();
		container.subscribeToTradeMessages();
		container.subscribeToSignals();
		container.filterOnlyMyTrades(true);
		
		implementation = getMathCaseImplementation();
		log("MathCase implementation detected to be " + implementation.getClass().getSimpleName());
		
		teamDB = container.getDB(teamCode);
		implementation.initializeAlgo(teamDB);
	}

	public void onMarketBidAsk(MarketBidAskMessage msg) {
		log("Received TOB " + msg.instrumentId);
		Prices prices = instruments().getAllPrices(msg.instrumentId);
		int result = implementation.newBidAsk(prices.bid, prices.ask);
		if (result > 0) {
			long id = trades().manualTrade(msg.instrumentId,
					 result,
					 prices.ask,
					 com.optionscity.freeway.api.Order.Side.BUY,
					 new Date(),
					 null, null, null, null, null, null);
			implementation.orderFilled(result, prices.ask);
		}
		else if (result < 0) {
			long id = trades().manualTrade(msg.instrumentId,
					 result,
					 prices.bid,
					 com.optionscity.freeway.api.Order.Side.SELL,
					 new Date(),
					 null, null, null, null, null, null);
			implementation.orderFilled(result, prices.bid);
		}
	}

	
}
