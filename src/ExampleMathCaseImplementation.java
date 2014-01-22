

import org.chicago.cases.AbstractMathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ExampleMathCaseImplementation extends AbstractMathCase {
	
	class MySampleImplementation implements MathCase {
		
		private IDB myDatabase;
		int factor;

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
			return 2; // always buy qty = 2
		}

		public void orderFilled(int volume, double fillPrice) {
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
		}
		
	}

	@Override
	public MathCase getMathCaseImplementation() {
		return new MySampleImplementation();
	}

}
