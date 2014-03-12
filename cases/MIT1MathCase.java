
import java.util.ArrayList;


import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a barebones sample of a MathCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class MIT1MathCase extends AbstractMathCase implements MathCase {        

		
		//private IDB myDatabase;
		
        final int WINDOW_SIZE = 9;
        final int WAIT_TICKS = 8;
        //final double THRESHOLD = 0.7;
        final int PRC_THRESHOLD = 40;
        final int PRC_THRESHOLD_SPREAD1 = 35;
        final int WAIT_SPREAD = 3;
        
        int tick = 0;
		int factor;
        int position = 0;
        int PnL = 0;
        int wait = 0;
        int wait_for_spread = 0;
        ArrayList<Integer> diffs;
        ArrayList<Integer> prices;
        //always in the following order [tick, position, price]
        ArrayList<Integer> futureOrder;

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("position", "checking our position", "int", "0");
		}

		public void initializeAlgo(IDB database) {
			
			// Databases can be used to store data between rounds
			//myDatabase = database;
			
			//initialize variables
			diffs = new ArrayList<Integer>();
			prices = new ArrayList<Integer>();
			futureOrder = new ArrayList<Integer>();
			
			// helper method for accessing declared variables
			factor = getIntVar("position"); 
		}

		public int newBidAsk(double bid, double ask) {
		    tick++;
		    int PnL_single = 0;
            // Receive new bid and ask
			
			//Collecting data
			int price = (int) ((bid+ask)/2);
            int diff;
			if (tick == 1) {
			    diff = 0;
			}
			else {
			    diff = price - prices.get(prices.size()-1);
			}
            diffs.add(diff);
            prices.add(price);
            int spread = (int) (price - bid);
            
            int sumChange = 0;
			int range = Math.max(0, tick - WINDOW_SIZE);
			for (int i = range; i < diffs.size(); i++)
			{
			    sumChange += diffs.get(i);
			}
			
	         log(tick + " Bid:" + bid + " Ask:" + ask + " WINDOW: " + sumChange);

			
			if (wait > 0)
			    wait--;
			
			
			if (!futureOrder.isEmpty() && futureOrder.get(0)<=tick)
			{
			    if (spread == 1 || wait_for_spread >= WAIT_SPREAD)
			    {
    		        //always in the following order [tick, position, lastPrice]
    			    int order = futureOrder.get(1);
    			    int enterPrice = futureOrder.get(2);
    			    
    			    if (order < 0) //Sell
    			        PnL_single = (int) (5 * (bid-enterPrice));
    			    else if (order > 0) //Buy
    			        PnL_single = (int) (5 * (enterPrice - ask));
 
    			    log ("PNL from trade: " + PnL_single);
    			    futureOrder.clear();
    			    wait_for_spread = 0;
    			    return order;
			    }
			    else 
			    {
			        wait_for_spread +=1;
			    }
			            
			}

			if ((position == 0) && (Math.abs(sumChange) > PRC_THRESHOLD_SPREAD1) && (wait == 0))
			{
			   if (Math.abs(sumChange) >= PRC_THRESHOLD || Math.abs(sumChange)<PRC_THRESHOLD && spread == 1)
			   {
			       if (sumChange > 0) //Price went up - we want to short
			       {
			           futureOrder.add(tick+WAIT_TICKS);
			           futureOrder.add(5);
			           futureOrder.add((int) bid);
			           wait = WAIT_TICKS;
			           log("SELL PLACED");
			           return -5;
			       }
			       else //Price went down - we want to buy
			       {
                       futureOrder.add(tick+WAIT_TICKS);
                       futureOrder.add(-5);
                       futureOrder.add((int) ask);		
                       wait = WAIT_TICKS;
                       log("BUY PLACED");
                       return 5;
			       }
			   }
			}
			
            return 0;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log(tick + "Order fill: " + volume + " Price: " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            position += volume;
            if (volume < 0)
                PnL += -1 * volume * fillPrice;
            else
                PnL -= volume * fillPrice;
            
            log(tick + " Position: " + position + " PNL: " + PnL);
		}

	

	public MathCase getMathCaseImplementation() {
		return (MathCase) this;
	}

}
