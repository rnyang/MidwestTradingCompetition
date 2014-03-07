

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ExampleArbCaseImplementation extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
		
		private IDB myDatabase;
		int factor;

		int position;
		double[] desiredRobotPrices = new double[2];
		double[] desiredSnowPrices = new double[2];
		
		double[] lastSnow = new double[2];
		double[] lastRobot = new double[2];

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			database.put("currentPosition", 10);
			int currentPosition = (Integer)database.get("currentPosition");
		}

		@Override
		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);
			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
			}else{
				position -= 1;
			}
			log("Current position: " + position);
			container.stopJob("Check Logs");
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("Pre-penalty position: " + position + "Last SNOW market: " + fmtMkt(lastSnow)
				+	" Last ROBOT market: " + fmtMkt(lastRobot));		                                                                                  
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
			for (Quote quote : quotes) {
				log("Algo received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}
			
			lastSnow[0]=quotes[0].bidPrice;
			lastSnow[1]=quotes[0].askPrice;
			
			lastRobot[0] = quotes[1].bidPrice;
			lastRobot[1] = quotes[1].askPrice;
			
			desiredRobotPrices = new double[2];
			desiredSnowPrices = new double[2];
			
			desiredRobotPrices[0] = quotes[0].bidPrice + 0.5;
			desiredRobotPrices[1] = quotes[0].askPrice + 20;

			desiredSnowPrices[0] = quotes[1].bidPrice + 0.5;
			desiredSnowPrices[1] = quotes[1].askPrice + 20;
		}

		@Override
		public Quote[] refreshQuotes() {
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
			quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
			log("My SNOW mkt: " + fmtMkt(desiredSnowPrices) + " My ROBOT mkt: " + fmtMkt(desiredRobotPrices));
			return quotes;
		}

	}

	
	private String fmtMkt(double[] mkt){
		return mkt[0] + "/" + mkt[1];
	}
	
	@Override
	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}