import java.util.*;

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

public class ILL2MathCase extends AbstractMathCase implements MathCase {


                private IDB myDatabase;
                int factor;
		        int contract = 0;
		        //double moving_average;
		        double moving_average_d = 0;
		        int tick = 1;
		        double average;
		        int state = 0;
				int position = 0;
                double STARTINGPRICE = 10000;
                double lastPrice = 0;
                double total_changePrice = 0;
                double totalPrice = 0;
                double variance = 1;
                double moving_spread = 0;
                double current_spread = 0;
                double spread_variance = 0;
                double total_spread = 0;
                double moving_average = 0;
                double profit = 0;
                double loss = 0;

                int STEP_SIZE = 10;
                
                double cash = 0;
                double stocks = 0;
                
                Queue<Double> price = new LinkedList<Double>();
		        Queue<Double> d_price = new LinkedList<Double>();
		        Queue<Double> spread = new LinkedList<Double>();
		        Queue<Double> d_spread = new LinkedList<Double>();

                public void addVariables(IJobSetup setup) {
                        // Registers a variable with the system.
                        setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
                }

                public void initializeAlgo(IDB database) {

                        // Databases can be used to store data between rounds
                        myDatabase = database;

                        // helper method for accessing declared variables
                        factor = getIntVar("someFactor");
                }

                public int newBidAsk(double bid, double ask) {
                        //log("I received a new bid of " + bid + ", and ask of " + ask);
                        
                        current_spread = (ask-bid)/2;
                        average = (ask+bid)/2;
                        if(tick < STEP_SIZE){
                                totalPrice += average;
                                total_spread += current_spread;                              
                        }

                        if(price.peek() != null) {
                                d_price.add(average-price.peek());
                   
                        } else{
                                d_price.add(average-STARTINGPRICE);
                        }

                        price.add(average);

                        spread.add(current_spread);
                        
                        tick = ++tick; //keeps track of ticks
                        
                        if(tick>=STEP_SIZE){
                   
                        	d_price.poll();
                        	
                        	totalPrice += average-price.poll();
                            moving_average = totalPrice/STEP_SIZE;
                            double sum = 0;                            
                            //double threshold = ((double)STEP_SIZE - (double)(1/5) * variance);
                            double threshold = (double)STEP_SIZE;
                            
                            if(threshold < 3)
                            	threshold = 3;
                           
                            int c = 0;
                            for(Double _price : d_price){
                            	sum += _price;
                            	c++;
                            	if(c > threshold)
                            		break;
                            }
                            moving_average_d = sum/threshold;
                            
                            sum = 0;
                            for(Double _price : d_price){
                                    sum += (_price-moving_average_d)*(_price-moving_average_d);
                            }
                            variance = sum/STEP_SIZE;

                            if(moving_average_d > 0){
                                    state = 1;
                            } else {
                                    state = -1;
                            }
                         
                            total_spread += current_spread - spread.poll();
                            moving_spread = totalPrice/STEP_SIZE;

                            spread_variance = 0;
                            for(Double _spread : spread){
                                    spread_variance += (_spread-moving_spread)*(_spread-moving_spread);
                            }
                            spread_variance = spread_variance/STEP_SIZE;

                        }

                        //log("tick = " + Integer.toString(tick) + " state = " + Integer.toString(state));
                        //log("drift = " + moving_average + " |||| volitality = " + variance);
                        double total;
						log("state = " + state);
                		if(tick >= STEP_SIZE)
                		{
                			if(state == 1){
                				//if(bid - moving_average < bid*0.03 - (10/Math.sqrt(variance))){
	                				cash -= bid;
	                				stocks += 1;
	                				total = cash + ask*stocks;
	                				log("Portfolio status ... cash = " + cash + "  stock_val = " + ask * stocks + " total = " + total);
									if(position <= 0){
										position = position + 5;
										return 5;
									}
                				//}
                				/** && if(stock price - moving average is negative) declare variable as something
                				 * if something is greater than 3% of stock price.
                				 * buy max position limit.
                				 */
                			} else {
                				//if(ask - moving_average > ask*0.03 - (10/Math.sqrt(variance))){
	                				cash += ask;
	                				stocks -= 1;
	                				total = cash + ask*stocks;
	                				log("Portfolio status ... cash = " + cash + "  stock_val = " + ask * stocks + " total = " + total);
	                				if(position >= 0){
										position = position - 5;
										return -5;
									}
                				//}
                			}
                		}
                		
                		
                		return 0;
                }


                // For your own benefit, keep track of your own PnL too
                public void orderFilled(int volume, double fillPrice) {

                        log("My order was filled with qty of " + volume + " at a price of " + fillPrice);

                        
                        
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            //log("My current position is: " + contract);

                }


        public MathCase getMathCaseImplementation() {
                return this;
        }

}