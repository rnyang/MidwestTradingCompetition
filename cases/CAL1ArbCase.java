import java.util.*;
import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class CAL1ArbCase extends AbstractExchangeArbCase {

	class MyArbImplementation implements ArbCase {

		// Note...the IDB will be used to save data to the hard drive and access
		// it later
		// This will be useful for retrieving data between rounds
		private IDB myDatabase;
		boolean arbitrage; 	/* Arbitrage round flag */
		boolean stop;		/* Stop for first two rounds */
		int position; /* Current position */
		int time; /* Current time */
		int numPast; /*
					 * Number of past time steps to look at when calculating
					 * projections
					 */
		int numTimeSteps; /* Total number of time steps in the simulation */
		int robotStreak; /*
						 * Flag for if price is currently falling or rising in
						 * ROBOT
						 */
		int snowStreak; /*
						 * Flag for if price is currently falling or rising in
						 * SNOW
						 */
		double threshold; /* Slope to sell/buy at */
		double pnl; /* Profit and losses */
		ArrayList<Double> robotBids = new ArrayList<Double>();
		ArrayList<Double> robotAsks = new ArrayList<Double>();
		ArrayList<Double> robotMids = new ArrayList<Double>();
		ArrayList<Double> snowBids = new ArrayList<Double>();
		ArrayList<Double> snowAsks = new ArrayList<Double>();
		ArrayList<Double> snowMids = new ArrayList<Double>();
		double[] desiredRobotPrices = new double[2];
		double[] desiredSnowPrices = new double[2];

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("numTimeSteps",
					"number of time steps in the simulation", "int", "1000");
			setup.addVariable("thresholdScale", "threshold slope scale", "double", "5.0");
			setup.addVariable("numPast", "number of past points to do linear regression on", 
					"int", "15");
			setup.addVariable("underlyingVol", "underlying volume", "double",
					"1.5");
			setup.addVariable("shockStd", "shock standard deviation", "double",
					"0.5");
			setup.addVariable("spreadMean", "spread mean", "double", "1.0");
			setup.addVariable("spreadStd", "spread standard deviation",
					"double", "0.3");
			setup.addVariable("p1", "p1", "double", "0.5");
			setup.addVariable("exMem", "ex mem", "double", "0.1");
			setup.addVariable("arbitrage", "arbitrage round flag", "int", "0");
			setup.addVariable("stop", "stop first two rounds", "int", "0");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			database.put("currentPosition", 10);

			time = 0;
			pnl = 0;
			numPast = getIntVar("numPast");
			numTimeSteps = getIntVar("numTimeSteps");
			robotStreak = 0;
			snowStreak = 0;
			threshold = getDoubleVar("underlyingVol") / getDoubleVar("thresholdScale");
			if (getIntVar("arbitrage") == 1) {
				arbitrage = true;
			} else {
				arbitrage = false;
			}
			if (getIntVar("stop") == 1) {
				stop = true;
			} else {
				stop = false;
			}
		}

		@Override
		public void fillNotice(Exchange exchange, double price,
				AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on "
					+ exchange + " as a " + algoside);
			if (algoside == AlgoSide.ALGOBUY) {
				position += 1;
				pnl -= price;
			} else if (algoside == AlgoSide.ALGOSELL) {
				position -= 1;
				pnl += price;
			}
			log("pnl is " + pnl + " position is " + position);
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity
					+ " positions cleared at " + price);
			position -= clearedQuantity;
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
			time += 1;
			log("time is " + time);
			// Update quotes (5 steps behind)
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice
						+ ", and ask of " + quote.askPrice + " from "
						+ quote.exchange);
				if (quote.exchange == Exchange.ROBOT) {
					robotBids.add(quote.bidPrice);
					robotAsks.add(quote.askPrice);
					robotMids.add((quote.bidPrice + quote.askPrice) / 2);
				} else if (quote.exchange == Exchange.SNOW) {
					snowBids.add(quote.bidPrice);
					snowAsks.add(quote.askPrice);
					snowMids.add((quote.bidPrice + quote.askPrice) / 2);
				}
			}

			ArrayList<Double> robotPast = new ArrayList<Double>();
			ArrayList<Double> snowPast = new ArrayList<Double>();
			
			/* If approaching position limits, drop everything and sell/buy */ 
			if (Math.abs(position) > 180) {
				for (int i = numPast; i > 0; i--) {
					robotPast.add(robotMids.get(robotMids.size() - i));
					snowPast.add(snowMids.get(snowMids.size() - i));
				}

				double[] robotEst = linearRegression(robotPast);
				double[] snowEst = linearRegression(snowPast);
				
				if (position > 0) {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = robotEst[0] * 4 + robotEst[1];
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = snowEst[0] * 4 + snowEst[1];
				} else {
					desiredRobotPrices[0] = robotEst[0] * 4 + robotEst[1];
					desiredRobotPrices[1] = 1000000000;
					desiredSnowPrices[0] = snowEst[0] * 4 + snowEst[1];
					desiredSnowPrices[1] = 1000000000;
				}
				
			}
			/* Arbitrage round */
			else if (arbitrage && (time - 4 > numPast)) {
				boolean robot_arbitrage = true;
				boolean snow_arbitrage = true;
				for (int i = 1; i <= 5; i++) {
					if (!(robotBids.get(robotBids.size() - i) > snowAsks.get(snowAsks.size() - i))) {
						robot_arbitrage = false;
					}
					if (!(snowBids.get(snowBids.size() - i) > robotAsks.get(robotAsks.size() - i))) {
						snow_arbitrage = false;
					}
				}
				
				for (int i = numPast; i > 0; i--) {
					robotPast.add(robotMids.get(robotMids.size() - i));
					snowPast.add(snowMids.get(snowMids.size() - i));
				}

				double[] robotEst = linearRegression(robotPast);
				double[] snowEst = linearRegression(snowPast);

				log("robot slope " + robotEst[0] + " snow slope " + snowEst[0]);
				log("robot b " + robotEst[1] + " snow b " + snowEst[1]);
				
				if (robot_arbitrage) {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = robotEst[0] * 4 + robotEst[1]
							- getDoubleVar("spreadMean") / 2
							- spreadFactor(Exchange.ROBOT) / 2;
					desiredSnowPrices[0] = snowEst[0] * 4 + snowEst[1]
							+ getDoubleVar("spreadMean") / 2
							+ spreadFactor(Exchange.SNOW) / 2;
					desiredSnowPrices[1] = 1000000000;
				} else if (snow_arbitrage) {
					desiredRobotPrices[0] = robotEst[0] * 4 + robotEst[1]
							+ getDoubleVar("spreadMean") / 2
							+ spreadFactor(Exchange.ROBOT) / 2;
					desiredRobotPrices[1] = 1000000000;
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = snowEst[0] * 4 + snowEst[1]
							- getDoubleVar("spreadMean") / 2
							- spreadFactor(Exchange.SNOW) / 2;
				} else {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = 1000000000;
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = 1000000000;
				}
			}
			/* If we have enough data points, send in a quote based on a
			 * linear regression projection (during not arbitrage round) */
			else if ((!arbitrage) && (!stop) && (time - 4 > numPast)) { // && (Math.abs(position) < (numTimeSteps - time))) {
				log((Math.abs(position) - (numTimeSteps - time)) + "");
				for (int i = numPast; i > 0; i--) {
					robotPast.add(robotMids.get(robotMids.size() - i));
					snowPast.add(snowMids.get(snowMids.size() - i));
				}

				double[] robotEst = linearRegression(robotPast);
				double[] snowEst = linearRegression(snowPast);

				log("robot slope " + robotEst[0] + " snow slope " + snowEst[0]);
				log("robot b " + robotEst[1] + " snow b " + snowEst[1]);

				/* If the slope of the regression is extreme enough, sell/buy*/
				if (robotEst[0] < -threshold) {
					robotStreak = -1;
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = robotEst[0] * 4 + robotEst[1]
						+ getDoubleVar("spreadMean") / 2
						+ spreadFactor(Exchange.ROBOT) / 2;
				} else if (robotEst[0] > threshold) {
					robotStreak = 1;
					desiredRobotPrices[0] = robotEst[0] * 4 + robotEst[1]
						- getDoubleVar("spreadMean") / 2
						- spreadFactor(Exchange.ROBOT) / 2;
					desiredRobotPrices[1] = 1000000000;
				}
				/* If the slope is relatively flat, sell/buy based on whether
				 * we were rising or dropping
				 */
				else if ((robotStreak == -1) && (position < 0)) {
					desiredRobotPrices[0] = robotEst[0] * 4 + robotEst[1];
					desiredRobotPrices[1] = 1000000000;
				} else if ((robotStreak == 1) && (position > 0)) {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = robotEst[0] * 4 + robotEst[1];
				} else {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = 1000000000;
				}

				/* Perform same logic on SNOW */
				if (snowEst[0] < -threshold) {
					snowStreak = -1;
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = snowEst[0] * 4 + snowEst[1]
						+ getDoubleVar("spreadMean") / 2
						+ spreadFactor(Exchange.SNOW) / 2;
				} else if (snowEst[0] > threshold) {
					snowStreak = 1;
					desiredSnowPrices[0] = snowEst[0] * 4 + snowEst[1]
						- getDoubleVar("spreadMean") / 2
						- spreadFactor(Exchange.SNOW) / 2;
					desiredSnowPrices[1] = 1000000000;
				} else if ((snowStreak == -1) && (position < 0)) {
					desiredSnowPrices[0] = snowEst[0] * 4 + snowEst[1];
					desiredSnowPrices[1] = 1000000000;
				} else if ((snowStreak == 1) && (position > 0)) {
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = snowEst[0] * 4 + snowEst[1];
				} else {
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = 1000000000;
				}

			} /* else if (Math.abs(position) >= numTimeSteps - time) {
				log("OMG");
				for (int i = numPast; i > 0; i--) {
					robotPast.add(robotMids.get(robotMids.size() - i));
					snowPast.add(snowMids.get(snowMids.size() - i));
				}

				double[] robotEst = linearRegression(robotPast);
				double[] snowEst = linearRegression(snowPast);
				
				if (position > 0) {
					desiredRobotPrices[0] = 0.0;
					desiredRobotPrices[1] = robotEst[0] * 4 + robotEst[1];
					desiredSnowPrices[0] = 0.0;
					desiredSnowPrices[1] = snowEst[0] * 4 + snowEst[1];
				} else {
					log("WAT");
					desiredRobotPrices[0] = robotEst[0] * 4 + robotEst[1];
					desiredRobotPrices[1] = 1000000000;
					desiredSnowPrices[0] = snowEst[0] * 4 + snowEst[1];
					desiredSnowPrices[1] = 1000000000;
				}
			} */ 
			/* Default to doing nothing */
			else {
				desiredRobotPrices[0] = 0.0;
				desiredRobotPrices[1] = 1000000000;
				desiredSnowPrices[0] = 0.0;
				desiredSnowPrices[1] = 1000000000;
			}
		}

		@Override
		public Quote[] refreshQuotes() {
			if (time < 4) {
				time = 4;
			}
			log("REFRESH time " + time);
			log("robot " + desiredRobotPrices[0] + " " + desiredRobotPrices[1]);
			log("snow " + desiredSnowPrices[0] + " " + desiredSnowPrices[1]);
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0],
					desiredRobotPrices[1]);
			quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0],
					desiredSnowPrices[1]);

			return quotes;
		}

		private double[] linearRegression(ArrayList<Double> nums) {
			double[] params = new double[2];
			double sumX = 0.0;
			double sumX2 = 0.0;
			double sumY = 0.0;
			double dotP = 0.0;
			int n = nums.size();
			for (int i = -n; i < 0; i++) {
				sumX += i;
				sumX2 += i * i;
				sumY += nums.get(i + n);
				dotP += i * nums.get(i + n);
			}
			params[0] = (n * dotP - sumX * sumY) / (n * sumX2 - sumX * sumX);
			params[1] = (sumY - params[0] * sumX) / ((double) n);

			return params;
		}

		private double spreadFactor(Exchange e) {
			double p1 = getDoubleVar("p1");
			double factor = 0.0;
			if (e == Exchange.ROBOT) {
				for (int i = 1; i <= 5; i++) {
					factor += p1 * Math.abs((robotMids.get(robotMids.size() - i)
						- robotMids.get(robotMids.size() - i - 1)));
				}
			} else {
				for (int i = 1; i <= 5; i++) {
					factor += p1 * Math.abs((snowMids.get(snowMids.size() - i)
							- snowMids.get(snowMids.size() - i - 1)));
				}
			}
			
			return factor;
		}
	}

	@Override
	public ArbCase getArbCaseImplementation() {
		return new MyArbImplementation();
	}

}
