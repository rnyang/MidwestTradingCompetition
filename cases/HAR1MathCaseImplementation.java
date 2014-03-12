
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

public class HAR1MathCaseImplementation extends AbstractMathCase implements MathCase {
	
		
		private IDB myDatabase;
		int factor;
        int contract = 0;
        double[] midhistory = new double[3000];
        double[] spreadhistory = new double[3000];
        int time = 0;
        int desiredposition = 0;
        double cash = 0;
        int traded = 0;
        
        public int truncate(int in) {
            if (in>5) {
                in = 5;
            }
            if (in<-5) {
                in = -5;
            }
            return in;
        }
        
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
			log("I received a new bid of " + bid + ", and ask of " + ask);
            
            midhistory[time] = ((bid+ask)/2);
            spreadhistory[time] = ((ask-bid)/2);
            time += 1;
            log("Current PnL:" + (cash+(contract*midhistory[(time-1)])));
/*            double temp = 0;
            if(time>30) {
                temp = (midhistory[time-1]-midhistory[time-31])/30;
            }
            if (temp>0.4) {
                desiredposition = 5;
            }
            if (temp<-0.4) {
                desiredposition = -5;
            }
            if (temp<=0.2 && temp >=-0.2) {
                desiredposition = 0;
            }
            log("Temp:" + temp);
            if (spreadhistory[time-1] < 1.1) {
                return (truncate(desiredposition-contract));
            }
            else {
                return 0;
            }*/
            if (time>400 && traded == 0) {
                if(spreadhistory[time-1] < 1.1) {
                    if((midhistory[time-1]-midhistory[0])>0) {
                        traded = 1;
                        return 5;
                    }
                    else {
                        traded = 1;
                        return -5;
                    }
                }
                else {
                    return 0;
                }
            }
            else {
                return 0;
            }
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			cash = cash - (volume*fillPrice);
            log("Current cash:" + cash);
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            log("My current position is: " + contract);
		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}
