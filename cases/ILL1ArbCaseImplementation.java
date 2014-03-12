import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

public class ILL1ArbCaseImplementation extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
        private IDB myDatabase;
        private int factor;

        private int position = 0;
        private double[] desiredRobotPrices = new double[2];
        private double[] desiredSnowPrices = new double[2];
        private int timeRemaining = 995;
        private HashMap<Exchange, ArrayList<Double>> bids = new HashMap<Exchange, ArrayList<Double>>();
        private HashMap<Exchange, ArrayList<Double>> asks = new HashMap<Exchange, ArrayList<Double>>();


		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
		}

		@Override
		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);
			if (algoside == AlgoSide.ALGOBUY) {
				position += 1;
			} else {
				position -= 1;
			}
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
            timeRemaining--;
            // Do nothing at the last second to avoid having unclosed positions
            if (timeRemaining < 2) {							
                myDatabase.put("currentPosition", position);		
                myDatabase.put("PnL", 0.0);
                System.out.println(bids.get(Exchange.ROBOT));
                System.out.println(asks.get(Exchange.ROBOT));
                System.out.println(bids.get(Exchange.SNOW));
                System.out.println(asks.get(Exchange.SNOW));
            }
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}
            // Arb opportunity in one direction
            if (quotes[0].askPrice < quotes[1].bidPrice) {
                desiredRobotPrices[0] = quotes[0].askPrice;		// Buy at Ask on ROBOT 		
                desiredRobotPrices[1] = quotes[1].bidPrice;		// Sell at bid on SNOW
                desiredSnowPrices[0] = quotes[0].askPrice;		
                desiredSnowPrices[1] = quotes[1].bidPrice;
            }
            // The other direction
            else if (quotes[1].askPrice < quotes[0].bidPrice) {
                desiredRobotPrices[0] = quotes[1].askPrice;
                desiredRobotPrices[1] = quotes[0].bidPrice;
                desiredSnowPrices[0] = quotes[1].askPrice;
                desiredSnowPrices[1] = quotes[0].bidPrice;
            }
            // Default case--assume the role of a Market Maker
            else {
                // Quote size based on spread size--use a competitive increment
                desiredRobotPrices[0] = quotes[0].bidPrice;
                desiredRobotPrices[1] = quotes[0].askPrice;
                desiredSnowPrices[0] = quotes[1].bidPrice;
                desiredSnowPrices[1] = quotes[1].askPrice;
            }
		}

		@Override
		public Quote[] refreshQuotes() {
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
			quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
			return quotes;
		}

	}

	@Override
	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}
