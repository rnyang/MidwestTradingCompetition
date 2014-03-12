import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.ArrayList;

public class MIT2ArbCase extends AbstractExchangeArbCase {
	
	class MyArbImplementation implements ArbCase {
		
		double reversionCoeff;
		double spreadConst;
		double biasThreshold;
		int roundThreshold;
		boolean tradeThisRound;
		double w5, w4, w3, w2, w1;
		
		int position = 0;
		double balance = 0;
		
		ArrayList<Double> snowBids = new ArrayList<Double>();
		ArrayList<Double> snowAsks = new ArrayList<Double>();
		ArrayList<Double> robotBids = new ArrayList<Double>();
		ArrayList<Double> robotAsks = new ArrayList<Double>();
		
		ArrayList<Double> truePrice = new ArrayList<Double>();
		ArrayList<Double> snow = new ArrayList<Double>();
		ArrayList<Double> robot = new ArrayList<Double>();

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("reversionCoeff", "Coefficient for the (true - exch) term", "double", "0");
			setup.addVariable("spreadConst", "Term added or subtracted from the bids", "double", "0");
			setup.addVariable("biasThreshold", "Threshold for keeping position in check", "int", "150");
			setup.addVariable("roundThreshold", "Threshold for starting to liquidate assets", "int", "950");
			setup.addVariable("tradeThisRound", "Whether we trade this round", "boolean", "true");
			setup.addVariable("w5", "Factor * {t-3}", "double", "0");
			setup.addVariable("w4", "Factor * {t-3}", "double", "0");
			setup.addVariable("w3", "Factor * {t-3}", "double", ".1");
			setup.addVariable("w2", "Factor * {t-2}", "double", ".3");
			setup.addVariable("w1", "Factor * {t-1}", "double", ".6");
		}

		public void initializeAlgo(IDB database) {
			reversionCoeff = getDoubleVar("reversionCoeff");
			spreadConst = getDoubleVar("spreadConst");
			biasThreshold = getIntVar("biasThreshold");
			roundThreshold = getIntVar("roundThreshold");
			tradeThisRound = getBooleanVar("tradeThisRound");
			w5 = getDoubleVar("w5");
			w4 = getDoubleVar("w4");
			w3 = getDoubleVar("w3");
			w2 = getDoubleVar("w2");
			w1 = getDoubleVar("w1");
		}

		@Override
		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);
			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
				balance -= price;
			}else{
				position -= 1;
				balance += price;
			}
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
			balance += clearedQuantity * price;
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
			log("my balance is " + balance + "; my position is " + position);
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}

			robotBids.add(quotes[0].bidPrice);
			robotAsks.add(quotes[0].askPrice);

			snowBids.add(quotes[1].bidPrice);
			snowAsks.add(quotes[1].askPrice);
			
			truePrice.add((quotes[0].bidPrice + quotes[0].askPrice + quotes[1].bidPrice + quotes[1].askPrice) / 4);
			robot.add((quotes[0].bidPrice + quotes[0].askPrice) / 2 );
			snow.add((quotes[1].bidPrice + quotes[1].askPrice) / 2);
		}

		@Override
		public Quote[] refreshQuotes() {
			double robotBid, robotAsk, snowBid, snowAsk;
			double bias;
			
			if (tradeThisRound){
				if (Math.abs(position) > biasThreshold){
					bias = -position / 25.0;
				} else if (truePrice.size() + Math.abs(position) > roundThreshold){
					bias = -position / 25.0;
				} else {
					bias = 0;
				}
				
				if (truePrice.size() <= 5){
					robotBid = 99.;
					robotAsk = 101.;
					snowBid = 99.;
					snowAsk = 101.;
				}
				else {
					robotBid = estimate(robotBids);
					robotAsk = estimate(robotAsks);
					snowBid = estimate(snowBids);
					snowAsk = estimate(snowAsks);

					robotBid += reversionCoeff * (estimate(truePrice) - estimate(robot)) + spreadConst + bias;
					robotAsk += reversionCoeff * (estimate(truePrice) - estimate(robot)) + spreadConst + bias;
					snowBid += reversionCoeff * (estimate(truePrice) - estimate(snow)) + spreadConst + bias;
					snowAsk += reversionCoeff * (estimate(truePrice) - estimate(snow)) - spreadConst + bias;
				}
			} else {
				snowBid = 0.;
				snowAsk = 200.;
				robotBid = 0.;
				robotAsk = 200.;
			}
			
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, robotBid, robotAsk);
			quotes[1] = new Quote(Exchange.SNOW, snowBid, snowAsk);
			return quotes;
		}
		
		private double estimate(ArrayList<Double> data){
			double result = 0;
			int length = data.size();
			
			result += w1 * data.get(length - 1);
			result += w2 * data.get(length - 2);
			result += w3 * data.get(length - 3);
			result += w4 * data.get(length - 4);
			result += w5 * data.get(length - 5);
			
			return result;
		}
	}

	@Override
	public ArbCase getArbCaseImplementation() {
		return new MyArbImplementation();
	}

}
