package org.chicago.cases;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.chicago.cases.CommonSignals.EndSignal;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IGrid;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;

public abstract class AbstractMathCase extends AbstractJob {
	
	private static final long STAT_REFRESH = 500;
	private static final String STAT_GRID = "MATH";
	private static final String MARKET_GRID = "MATH_MARKET";
	
	
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
	
	class TradeInfo {
		
		final int position;
		final double price;
		
		private TradeInfo(int position, double price) {
			this.position = position;
			this.price = price;
		}
		
	}
	
	// ----------- Handle System Events and Translate to Case Interface Methods ---------------
	
	private IDB teamDB;
	private MathCase implementation;
    private int position = 0;
    private IGrid statsGrid;
    private IGrid marketGrid;
    private String teamCode;
    private String underlying;
    private List<TradeInfo> trades = new ArrayList<TradeInfo>();
	
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
		setup.setVariable("timer", "" + STAT_REFRESH);
	}
	
	private double calculatePNL() {
		Prices prices = instruments().getAllPrices(underlying);
		double settlement = (prices.ask + prices.bid) / 2;
		double pnl = 0;
		for (TradeInfo trade : trades) {
			double cost = trade.position * trade.price;
			double value = trade.position * settlement;
			pnl += value - cost;
		}
		return pnl;
	}

	public void onTimer() {
		statsGrid.set(teamCode, "position", position);
		statsGrid.set(teamCode, "pnl", calculatePNL());
		
		Prices prices = instruments().getAllPrices(underlying);
		marketGrid.set(underlying, "bid", prices.bid);
		marketGrid.set(underlying, "offer", prices.ask);
		
	}

	/*
	 * Begin() is called when the job is started.  I've wanted to hide this from them because it gives them
	 * access to the container which would allow them to mess w/ system settings.
	 */
	@Override
	public void begin(IContainer container) {
		super.begin(container);
		teamCode = getStringVar("Team_Code");
		if (teamCode.isEmpty())
			container.stopJob("Please set a Team_Code in the configuration");
		if (!TeamUtilities.validateTeamCode(teamCode))
			container.stopJob("The specified Team Code is not a valid code.  Please enter the code provided to your team.");
		statsGrid = container.addGrid(STAT_GRID, new String[] {"position", "pnl"});
		marketGrid = container.addGrid(MARKET_GRID, new String[] {"bid", "offer"});
		log("Team Code is, " + teamCode);
		
		List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.MATH, teamCode);
		for (String product : products) {
			underlying = product + "-E";
			instruments().startSymbol(product);
			container.filterMarketMessages(product + ";;;;;;");
			log("filtering for " + product);
		}
		
		container.subscribeToMarketBidAskMessages();
		container.subscribeToTradeMessages();
		container.subscribeToSignals();
		container.filterOnlyMyTrades(true);
		container.getPlaybackService().register(new CommonSignalProcessor());
		
		implementation = getMathCaseImplementation();
		log("MathCase implementation detected to be " + implementation.getClass().getSimpleName());
		
		teamDB = container.getDB(teamCode);
		implementation.initializeAlgo(teamDB);
	}
	
	public void onSignal(EndSignal msg) {
		log("END signal received");
	}

	public void onMarketBidAsk(MarketBidAskMessage msg) {

		Prices prices = instruments().getAllPrices(msg.instrumentId);
		int result = implementation.newBidAsk(prices.bid, prices.ask);

        /*--------------------------------Hanzhi Update----------------------------------------*/
        int newPosition = position + result;
        if (newPosition > 5 || newPosition < -5){
        	log("You have exceeded the position limits.  Your order will not be filled");
        	log("The requested amount would have put your position at " + newPosition);
            implementation.orderFilled(0,0);
            return;
        }
		/*--------------------------------Hanzhi Update----------------------------------------*/

        if (result > 0) {
			long id = trades().manualTrade(msg.instrumentId,
					 result,
					 prices.ask,
					 com.optionscity.freeway.api.Order.Side.BUY,
					 new Date(),
					 null, null, null, null, null, null);
			implementation.orderFilled(result, prices.ask);
			trades.add(new TradeInfo(result, prices.ask));
			position += result;
		}
		else if (result < 0) {
			long id = trades().manualTrade(msg.instrumentId,
					 result,
					 prices.bid,
					 com.optionscity.freeway.api.Order.Side.SELL,
					 new Date(),
					 null, null, null, null, null, null);
			implementation.orderFilled(result, prices.bid);
			trades.add(new TradeInfo(result, prices.bid));
			position += result;
		}
        
	}

	
}
