
import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import java.util.*;


public class  CAL2MathCaseImplementation extends AbstractMathCase implements MathCase {
	
		
		private IDB myDatabase;
		int factor;
        double prev_val = 0;
        ArrayList<Double> curr_arrayList = new ArrayList<Double>();
        int curr_arrayList_idx = 0;
        double positive_drift = 0;
        int total_profit = 0;
        boolean purchased = false;
        boolean sold = false;
        double price_at_purchase = 0;
        int num_bids = 0;
        int contract = 0;
        double price_at_short = 0;
        double currposition = 0;
        // Number of steps required until we update trend
        //int NUM_STEPS_TILL_UPDATE = 50;
        // Amount of upward trend required to go long
        //double UPWARDS_THRESHOLD = 0.6;
        // Amount of downward trend required to go short
        //double DOWNWARD_THRESHOLD = 0.6;
        // Price difference required to clear out of position
        //int PRICE_THRESHOLD = 30;
        
        int NUM_STEPS_TILL_UPDATE = 0;
        // Amount of upward trend required to go long
        double UPWARDS_THRESHOLD = 0;
        // Amount of downward trend required to go short
        double DOWNWARD_THRESHOLD = 0;
        // Price difference required to clear out of position
        int PRICE_THRESHOLD = 0;
        int NO_TRADE;
        int MIN_NUM_BIDS;
        int PRICE_MAX_LOSS;
		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
            setup.addVariable("NUM_STEPS_TILL_UPDATE", "constant used to adjust number of steps until update", "int", "50");
            setup.addVariable("UPWARDS_THRESHOLD", "constant used to adjust amount of upward drift observed until we clear a long position (between 0 and 1)", "double", "0.6");
            setup.addVariable("DOWNWARD_THRESHOLD", "constant used to adjust amount of downward drift observed until we clear a short position (between 0 and 1)", "double", "0.7");
            setup.addVariable("PRICE_THRESHOLD", "constant used to adjust amount of profit seen until we clear out of position", "int", "30");
            setup.addVariable("MIN_NUM_BIDS", "Number of bids that must be observed before we start bidding", "int", "50");            
            setup.addVariable("NO_TRADE", "1 indicates no more trades", "int", "0");
            setup.addVariable("PRICE_MAX_LOSS", "Maximum loss before we clear and try to cut losses", "int", "30");
		}

		public void initializeAlgo(IDB database) {
			
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			factor = getIntVar("someFactor"); 
            NUM_STEPS_TILL_UPDATE = getIntVar("NUM_STEPS_TILL_UPDATE");
            UPWARDS_THRESHOLD = getDoubleVar("UPWARDS_THRESHOLD");
            DOWNWARD_THRESHOLD  = getDoubleVar("DOWNWARD_THRESHOLD");
            PRICE_THRESHOLD = getIntVar("PRICE_THRESHOLD");
            NO_TRADE = getIntVar("NO_TRADE");
            PRICE_MAX_LOSS = getIntVar("PRICE_MAX_LOSS");
		}

		public int newBidAsk(double bid, double ask) {
            // Do not trade 
            if(NO_TRADE == 1)
                return 0;
			log("I received a new bid of " + bid + ", and ask of " + ask);
            // Receive new bid and ask
            // Calculate current evaluation of good
            double current_value = (bid + ask)/2;
            // We have received one more bid
            num_bids++;
            
            // At every increment of NUM_STEPS_TILL_UPDATE bid, we check to
            // see how our evaluation of the good has changed
            // by recording how much of an upward trend there was
            if(num_bids % NUM_STEPS_TILL_UPDATE == 0)
            {
                if(curr_arrayList_idx < 3)
                {
                    if(prev_val < current_value)
                        positive_drift += 1;                
                }
                else
                {
                    if(num_bids < 500)
                    {
                        if(prev_val < current_value)
                            positive_drift += 0.3;
                        if(curr_arrayList.get(curr_arrayList_idx-2) < current_value)
                            positive_drift += 0.3;
                        if(curr_arrayList.get(curr_arrayList_idx-3) < current_value)
                            positive_drift += 0.4;                       
                    }
                    else
                    {
                        if(prev_val < current_value)
                            positive_drift += 0.4;
                        if(curr_arrayList.get(curr_arrayList_idx-2) < current_value)
                            positive_drift += 0.3;
                        if(curr_arrayList.get(curr_arrayList_idx-3) < current_value)
                            positive_drift += 0.3;                            
                    }
                }
                curr_arrayList.add(current_value);
                curr_arrayList_idx++;                
                prev_val = current_value;
            }
            // If we have not yet observed enough data, 
            // do not do anything
            if(num_bids < MIN_NUM_BIDS)
                return 0;
            int temp = (int)(num_bids/NUM_STEPS_TILL_UPDATE);
            log("Price_at_purchase : " + price_at_purchase);
            log("Positive Drift: " + (double)positive_drift/(double)temp);
            log("Time : " + num_bids);
            // If there is enough upward trend from the past observed
            // prices, and we have yet to buy any, purchase some of the goods
            if((double)positive_drift/(double)temp > UPWARDS_THRESHOLD && !purchased && !sold)
            {
                purchased = true;
                log("purchased 5 at price of " + ask);
                price_at_purchase = ask;
                return 5;
            }
            // Short if there is enough negative trend
            else if(1.0-(double)positive_drift/(double)temp > DOWNWARD_THRESHOLD && !sold && !purchased)
            {
                sold = true;
                log("sold 5 at price of " + bid);
                price_at_short = bid;
                return -5;
            }
            // If we have already purchased goods and the current
            // price is greater than our original purchase price
            // by some threshold, sell them
            else if(purchased && bid - price_at_purchase > PRICE_THRESHOLD)
            {
                log("sold 5 at price of " + bid);
                log("Profit of " + (int)(bid - price_at_purchase));
                total_profit += (int)(5 * (bid - price_at_purchase));
                log("total profit: " + total_profit);                
                purchased = false;
                return -5;
            }
            // If we have already shorted the goods and the
            // current ask is high enough, then buy some
            // to clear position
            else if(sold && price_at_short - ask > PRICE_THRESHOLD)
            {
                log("bought 5 at price of " + ask);
                log("Profit of " + (int)(price_at_short - ask));
                total_profit += (int)(5 * (price_at_short - ask));
                log("total profit: " + total_profit);                
                sold = false;
                return 5;
            }
            // If I have already purchased some of the goods and
            // the trend seems to be extremely negative and the
            // price of the product has undergone my original purchase
            // price by some threshold, go short instead
            else if(purchased && 1.0-(double)positive_drift/(double)temp > DOWNWARD_THRESHOLD && price_at_purchase - current_value > PRICE_MAX_LOSS)
            {
                log("sold 10 at price of " + bid);
                log("Profit of " + (int)(bid - price_at_purchase));
                total_profit += (int)(10 * (bid - price_at_purchase));
                log("total profit: " + total_profit);                
                purchased = false;
                sold = true;
                return -10;                
            }
            // If I have already shorted some of the goods and
            // the trend seems to be extremely positive and the
            // price of the product has went over my original purchase
            // price by some threshold, go long instead            
            else if(sold && (double)positive_drift/(double)temp > UPWARDS_THRESHOLD &&  current_value - price_at_short > PRICE_MAX_LOSS)
            {
                log("bought 10 at price of " + ask);
                log("Profit of " + (int)(price_at_short - ask));
                total_profit += (int)(10 * (price_at_short - ask));
                log("total profit: " + total_profit);                
                purchased = true;
                sold = false;
                return 10;
            }
            else
            {
                log("total profit: " + total_profit);
                return 0;
            }
            
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            currposition -= volume*fillPrice;
            double currprofit = currposition + contract * fillPrice;
            log("Current profit is : " + currprofit);
            log("My current position is: " + contract);

		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}