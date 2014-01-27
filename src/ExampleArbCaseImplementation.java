

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ExampleArbCaseImplementation extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
		
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
		
		@Override
		public void initialize(Quote[] startingQuotes) {
			for (Quote quote : startingQuotes) {
				log("Initial bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}
		}

		@Override
		public void fillNotice(Exchange exchange, double price) {
			log("My quote was filled with at a price of " + price + " on " + exchange);
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}
		}

		@Override
		public Quote[] refreshQuotes() {
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, 100.00, 102.00);
			quotes[1] = new Quote(Exchange.SNOW, 101.00, 103.00);
			return quotes;
		}

	}

	@Override
	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}
