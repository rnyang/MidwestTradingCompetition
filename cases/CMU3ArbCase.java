

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class CMU3ArbCase extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
		
		// Note...the IDB will be used to save data to the hard drive and access it later
		// This will be useful for retrieving data between rounds
		private IDB myDatabase;
		int factor;

		int position;
		double realizedGains;
		double[] desiredRobotPrices;
		double[] desiredSnowPrices;

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


		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);

			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
				realizedGains -= price;
			}else{
				position -= 1;
				realizedGains += price;
			}
		}


		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		}

		public void newTopOfBook(Quote[] quotes) {
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}
			
			if(position > 150){
				desiredRobotPrices[0] = (quotes[0].bidPrice + quotes[1].bidPrice)/2.0;
				desiredRobotPrices[1] = Math.min(quotes[0].askPrice, quotes[1].askPrice);
			}
			else{
				if(position < -150){
					desiredRobotPrices[0] = Math.min(quotes[0].bidPrice, quotes[1].bidPrice);
					desiredRobotPrices[1] = (quotes[0].askPrice + quotes[1].askPrice)/2.0;
				}
				else{
					desiredRobotPrices[0] = (quotes[0].bidPrice + quotes[1].bidPrice)/2.0;
					desiredRobotPrices[1] = (quotes[0].askPrice + quotes[1].askPrice)/2.0;
				}
			}
		}


		public Quote[] refreshQuotes() {
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
			quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
			return quotes;
		}

	}

	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}
