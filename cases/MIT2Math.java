
import java.util.ArrayList;
import java.util.List;

import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


/*
 * $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$
 */

public class MIT2Math extends AbstractMathCase implements MathCase {		
		private IDB myDatabase;
		int factor;
        
        // Parameters that can be configured.
        int priceChangeWindowSize;
        int ticksToWaitBeforeBuyingBack;
        int ticksToWaitForSmallSpread;
        int priceChangeThresholdSmallSpread;
        int priceChangeThresholdLargeSpread;
        int totalTicks;
        
        // State variables.
        int numTrades;
        int ticksLeftBeforeBuyBack;
        int ticksWaitedForSmallSpread;
        int currentTick;
        int totalPnL;
        int position;
        
        // State Lists.
        List<List<Integer>> futureTrades;
        List<Integer> prices;

		public void addVariables(IJobSetup setup) {
			//  variables with the system.
			setup.addVariable("priceChangeWindowSize", "Lookback window for price change.", "int", "8");
			setup.addVariable("ticksToWaitBeforeBuyingBack", "After putting on a position, how long to reverse.", "int", "8");
			setup.addVariable("ticksToWaitForSmallSpread", "If the spread is not small when reversing, how long to wait before reversing anyways.", "int", "3");
			setup.addVariable("priceChangeThresholdSmallSpread", "Price change threshold, spread=1.", "int", "35");
			setup.addVariable("priceChangeThresholdLargeSpread", "Price change threshold, spread>1.", "int", "40");
			setup.addVariable("totalTicks", "Total number of ticks in the case.", "int", "1000");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// Parameters that can be configured.
			priceChangeWindowSize = getIntVar("priceChangeWindowSize"); // 8;
			ticksToWaitBeforeBuyingBack = getIntVar("ticksToWaitBeforeBuyingBack"); // 8;
			ticksToWaitForSmallSpread = getIntVar("ticksToWaitForSmallSpread"); // 3;
			priceChangeThresholdSmallSpread = getIntVar("priceChangeThresholdSmallSpread"); // 35;
			priceChangeThresholdLargeSpread = getIntVar("priceChangeThresholdLargeSpread"); // 40;
			totalTicks = getIntVar("totalTicks"); // 1000;
			
			// State variables.
			numTrades = 0;
			ticksLeftBeforeBuyBack = 0;
			ticksWaitedForSmallSpread = 0;
			currentTick = -1;
			totalPnL = 0;
			position = 0;
			
			// Initialize lists.
			futureTrades = new ArrayList<List<Integer>>();
			prices = new ArrayList<Integer>();
			
			log("Initialized stuff!");
		}

		public int newBidAsk(double bidFloat, double askFloat) {
		    currentTick += 1;
		    
		    // If we won't be able to liquidate in time, don't play.
		    if (currentTick >= totalTicks - Math.max(priceChangeWindowSize, ticksToWaitBeforeBuyingBack)) {
		        log("Not playing.");
		        return 0;
		    }
		    
		    // Get the bid, ask, price, spread.
	        int bid = (int) bidFloat;
	        int ask = (int) askFloat;
		    log("Tick " + currentTick + ": " + bid + " / " + ask);
		    int price = (bid + ask) / 2;
		    int spread = price - bid;
		    
		    // Append latest price.
		    prices.add(price);
		    
		    // Compute the change in price from a few ticks ago.
		    int change = price - prices.get(Math.max(0, prices.size() - priceChangeWindowSize - 2));
		    int absChange = Math.abs(change);
		    
		    // Check if we need to buy back or sell back.
		    if ((futureTrades.size() > 0) && (futureTrades.get(0).get(0) <= currentTick)) {
		        List<Integer> future_trade = futureTrades.get(0);
		        int side = future_trade.get(1);
		        int enterPrice = future_trade.get(2);
		        if ((spread == 1) || (ticksWaitedForSmallSpread >= ticksToWaitForSmallSpread)) {
		            int trade_to_do;
		            if (side == -1) {
		                // Sell now.
		                int pnl = -5 * (enterPrice - bid);
		                totalPnL += pnl;
		                numTrades += 1;
		                log("Selling back now; pnl: " + pnl + "; totalPnL: " + totalPnL + "; numTrades: " + numTrades);
		                trade_to_do = -5;
		            } else {
		                // Buy now.
		                int pnl = 5 * (enterPrice - ask);
                        totalPnL += pnl;
                        numTrades += 1;
                        log("Buying back now; pnl: " + pnl + "; totalPnL: " + totalPnL + "; numTrades: " + numTrades);
		                trade_to_do = 5;
		            }
		            futureTrades.remove(0);
		            ticksWaitedForSmallSpread = 0;
		            
		            return trade_to_do;
		        } else {
		            ticksWaitedForSmallSpread += 1;
		        }
		        
		    }
		    
		    // If we still have to wait a few ticks to buy back, decrement that counter.
		    if (ticksLeftBeforeBuyBack > 0) {
		        ticksLeftBeforeBuyBack -= 1;
		    }
		    
		    // If we are not flat and didn't flatten already, don't trade.
		    if (position != 0) {
		        return 0;
		    }
		    
		    // If we are playing the waiting game before we buy back, don't trade.
		    if (ticksLeftBeforeBuyBack != 0) {
		        return 0;
		    }
		    
		    // We either need to beat the large threshold, or beat the small threshold with spread == 1.
		    if ((absChange >= priceChangeThresholdLargeSpread) || (spread == 1 && absChange >= priceChangeThresholdSmallSpread)) {
                // We passed the threshold. Put on a mean-reversion trade; e.g. if it was a positive change, sell.
		        if (change > 0) {
                    // Enter a trade to buy 5 in the future.
                    List<Integer> futureTrade = new ArrayList<Integer>();
                    // futureTrade = (reversalTick, futureSide, enterPrice)
                    futureTrade.add(currentTick + ticksToWaitBeforeBuyingBack);
                    futureTrade.add(1);
                    futureTrade.add(bid);
                    futureTrades.add(futureTrade);
                    
                    // Sell 5 now.
                    numTrades += 1;
                    log("Sold five; numTrades: " + numTrades);
                    ticksLeftBeforeBuyBack = ticksToWaitBeforeBuyingBack;
                    return -5;
                } else {
                    // Enter a trade to sell 5 in the future.
                    List<Integer> futureTrade = new ArrayList<Integer>();
                    // futureTrade = (reversalTick, futureSide, enterPrice)
                    futureTrade.add(currentTick + ticksToWaitBeforeBuyingBack);
                    futureTrade.add(-1);
                    futureTrade.add(ask);
                    futureTrades.add(futureTrade);
                    
                    // Sell 5 now.
                    ticksLeftBeforeBuyBack = ticksToWaitBeforeBuyingBack;
                    numTrades += 1;
                    log("Bought five; numTrades: " + numTrades);
                    return 5;
                }
		    }
		    
		    // No trade.
		    return 0;
		}

        // For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
		    position += volume;
			log("[Confirmed fill for " + volume + " at " + (int) fillPrice + "; position: " + position + "]");
		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}
