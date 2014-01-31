
import org.chicago.cases.AbstractMathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ExampleMathCaseImplementation extends AbstractMathCase {
	
	class MySampleMathImplementation implements MathCase {
		
		private IDB myDatabase;
		int factor;
        int contract = 0;

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			factor = getIntVar("someFactor"); // helper method for accessing declared variables
		}

		public int newBidAsk(double bid, double ask) {
			log("I received a new bid of " + bid + ", and ask of " + ask);
            // Receive new bid and ask
            // Input your HMM algorithm here to calculate how much to buy or sell
            if(bid<10000){
                return 2;
            }else{
                return -2;
            }
		}

		public void orderFilled(int volume, double fillPrice) {
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            log("My current position is: " + contract);
            if(contract>5 || contract<-5){
                log("Position error: " + contract);
            }

		}

        // For your own benefit, keep track of your own PnL too

	}

	@Override
	public MathCase getMathCaseImplementation() {
		return new MySampleMathImplementation();
	}

}
