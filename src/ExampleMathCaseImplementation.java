
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

public class ExampleMathCaseImplementation extends AbstractMathCase implements MathCase {
	
		
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
			
			// helper method for accessing declared variables
			factor = getIntVar("someFactor"); 
		}

		public int newBidAsk(double bid, double ask) {
			log("I received a new bid of " + bid + ", and ask of " + ask);
            // Receive new bid and ask
            // Input your HMM algorithm here to calculate how much to buy or sell
            return 1;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            log("My current position is: " + contract);

		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}
