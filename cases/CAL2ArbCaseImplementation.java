
import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.lang.Math;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.Queue;


public class CAL2ArbCaseImplementation extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
		
		private static final double TIME_DELAY = 5;
		//private static final int TOTAL_TIME = 1000;
		private static final int TIME_WINDOW = 50;

		private IDB myDatabase;
		int NUM_STD_DEV;
		int NUM_MOVE_UP;
		int NUM_MOVE_DOWN;
		int TRADE;

		int position;
		double[] desiredRobotPrices = new double[2];
		double[] desiredSnowPrices = new double[2];

		int time = 0;

		double capital = 0;
		double midpoint;

		double[] robotPrices = new double[2];
		double[] snowPrices = new double[2];

		double[] snowHist = new double[4];
		double[] robotHist = new double[4];

		Queue<Double> sampleWindow = new LinkedList<Double>();
		Queue<Double> totalWindow = new LinkedList<Double>();


		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("NUM_STD_DEV", "standard deviation of spread", "int", "4");
			setup.addVariable("NUM_MOVE_UP", "number of consecutive movements up", "int", "2");
			setup.addVariable("NUM_MOVE_DOWN", "number of consecutive movements down", "int", "2");
			setup.addVariable("TRADE", "should we trade?", "int", "1");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			NUM_STD_DEV = getIntVar("NUM_STD_DEV"); 
			NUM_MOVE_UP = getIntVar("NUM_MOVE_UP");
			NUM_MOVE_DOWN = getIntVar("NUM_MOVE_DOWN");
			TRADE = getIntVar("TRADE");
		}


		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			// your fillNotice was from the past
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside + " from time : " + (time-TIME_DELAY));

			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
				capital -= price;
			}else{
				position -= 1;
				capital += price;
			}
			log("Current time : " + time + ", My current profit is " + getProfit() + ", current position is " + position);
		}


		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		}

		public void newTopOfBook(Quote[] quotes) {
			robotPrices[0] = quotes[0].bidPrice;
			robotPrices[1] = quotes[0].askPrice;
			snowPrices[0] = quotes[1].bidPrice;
			snowPrices[1] = quotes[1].askPrice;

			double robotMid = (robotPrices[0] + robotPrices[1])/2;
			double snowMid = (snowPrices[0] + snowPrices[1])/2;

			log("Current time : " + (time+1) + ", New Bid : ROBOT[" + robotPrices[0] + ", " + robotPrices[1] + "], SNOW[" + snowPrices[0] + ", " + snowPrices[1] + "]");

			midpoint = (quotes[0].bidPrice + quotes[0].askPrice + quotes[1].bidPrice + quotes[1].askPrice) / 4;
			//prices[time] = midpoint;
			sampleWindow.add(midpoint);
			maintainSampleSize();
			totalWindow.add(midpoint);

			int update = checkChange(robotMid, snowMid);

			if (update == 0){
				addRobotHist(robotMid);
			}
			else if (update == 1){
				addSnowHist(snowMid);
			}
			else{
				addRobotHist(robotMid);
				addSnowHist(snowMid);
			}

			log("Current time : " + (time+1) + ", mean is " + getSampleMean() + ", variance is " + getSampleVariance());

			double stdDev = getSampleStdDev();
			double market = NUM_STD_DEV * stdDev;

			desiredRobotPrices[0] = quotes[0].bidPrice - market;
			desiredRobotPrices[1] = quotes[0].askPrice + market;

			desiredSnowPrices[0] = quotes[1].bidPrice - market;
			desiredSnowPrices[1] = quotes[1].askPrice + market;

			if (robotPrices[0] < snowPrices[1] && robotPrices[0] > snowPrices[0]){
				// check if robot is on the up
					if (getNumRobotIncrease() > NUM_MOVE_UP && getNumSnowDecrease() < NUM_MOVE_DOWN){
						// robot is probably increasing relative to snow
						desiredRobotPrices[1] = robotPrices[0];
						desiredSnowPrices[0] = snowPrices[1];

						desiredRobotPrices[0] = desiredRobotPrices[1] - market;
						desiredSnowPrices[1] = desiredSnowPrices[0] + market;
						log("Robot pls go up dongerino pasterino pls");

					}
				}
			else if(snowPrices[0] < robotPrices[1] && snowPrices[0] > robotPrices[1]){
				// check if snow is on the up
					if (getNumRobotDecrease() > NUM_MOVE_DOWN && getNumSnowIncrease() < NUM_MOVE_UP){
						// snow is probably increasing relative to robot
						desiredSnowPrices[1] = snowPrices[0];
						desiredRobotPrices[0] = robotPrices[1];

						desiredRobotPrices[1] = desiredRobotPrices[0] + market;
						desiredSnowPrices[0] = desiredSnowPrices[1] - market;
						log("Snow Pls Go up thx bb");
					}
				}

		}


		public Quote[] refreshQuotes() {
			time += 5; // called every 5 ticks
		
			log("Current time : " + time + ", New Quotes : ROBOT[" + desiredRobotPrices[0] + ", " + desiredRobotPrices[1] + "], SNOW[" + desiredSnowPrices[0] + ", " + desiredSnowPrices[1] + "]");
			Quote[] quotes = new Quote[2];
			if (TRADE == 1) {
				quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
				quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
			}
			else {
				quotes[0] = new Quote(Exchange.ROBOT, 0, midpoint*1000);
				quotes[1] = new Quote(Exchange.SNOW, 0, midpoint*1000);
			}

			resetHist();

			return quotes;
		}

		/* HELPER FUNCTIONS */

		private double getProfit() {
			return capital + position * midpoint;
		}

		// Functions for calculating sample window statistics
		private double getSampleMean() {
			int size = sampleWindow.size();
			double sum = 0;
			Iterator it = sampleWindow.iterator();
			while (it.hasNext()) {
				Double d = (Double) it.next();
				sum += d;
			}
			if (size == 0)
				return 0;
			return sum / size;
		}

        private double getSampleVariance() {
            double mean = getSampleMean();
            int size = sampleWindow.size();
            double temp = 0;
            Iterator it = sampleWindow.iterator();
            while (it.hasNext()) {
            	Double d = (Double) it.next();
            	temp += (mean-d)*(mean-d);
            }
            if (size <= 1)
            	return 0;
            return temp / (size - 1);
        }

        private double getSampleStdDev() {
        	return Math.sqrt(getSampleVariance());
        }
    
    	private void maintainSampleSize() {
    		while (sampleWindow.size() > TIME_WINDOW) 
    			sampleWindow.poll();
    	}


    	// Functions for calculating total window statistics
    	private double getTotalMean() {
			int size = totalWindow.size();
			double sum = 0;
			Iterator it = totalWindow.iterator();
			while (it.hasNext()) {
				Double d = (Double) it.next();
				sum += d;
			}
			if (size == 0)
				return 0;
			return sum / size;
		}

        private double getTotalVariance() {
            double mean = getTotalMean();
            int size = totalWindow.size();
            double temp = 0;
            Iterator it = totalWindow.iterator();
            while (it.hasNext()) {
            	Double d = (Double) it.next();
            	temp += (mean-d)*(mean-d);
            }
            if (size <= 1)
            	return 0;
            return temp / (size - 1);
        }

        private double getTotalStdDev() {
        	return Math.sqrt(getTotalVariance());
        }


        private void resetHist(){
        	for (int i=0; i <4; i++){
        		robotHist[i] = 0;
        		snowHist[i] = 0;
        	}
        }

        private int checkChange(double robotPrice, double snowPrice){
        	int change = 0;
        	if (robotPrice == robotHist[0]){
        		change = 1; // only snow changed
        	}
        	else if (snowPrice == snowHist[0]){
        		change = 0; // only robot changed
        	}
        	else {
        		change = 2; // both changed
        	}
        	return change;
        }

        private void addRobotHist(double price) {
        /* previous 4 time steps of robot prices */
        	int moreThan4 = 1;
        	for (int i= 0; i < 4; i++){
        		if (robotHist[i] == 0){
        			robotHist[i] = price;
        			moreThan4 = 0;
        			break;
        		}
        	}
        	if (moreThan4 == 1){
        		for (int i = 3; i > 0; i--){
					robotHist[i] = robotHist[i-1];
				}
				robotHist[0] = price;
        	}

        }

        private void addSnowHist(double price) {
        /* previous 4 time steps of robot prices */
        	int moreThan4 = 1;
        	for (int i= 0; i< 4; i++){
        		if (snowHist[i] == 0){
        			snowHist[i] = price;
        			moreThan4 = 0;
        			break;
        		}
        	}
        	if (moreThan4 == 1){
        		for (int i = 3; i > 0; i--){
					snowHist[i] = snowHist[i-1];
				}
				snowHist[0] = price;
        	}
        }
        	
        private int getNumSnowIncrease(){
        	int numInc = 0;
        	for (int i = 1; i < 4; i++){
        		if (snowHist[i] > snowHist[i-1]){
        			numInc++;
        		}
        		else{
        			break;
        		}

        	}
        	return numInc;
        }

        private int getNumSnowDecrease(){
        	int numDec = 0;
        	for (int i = 1; i < 4; i++){
        		if (snowHist[i] < snowHist[i-1]){
        			numDec++;
        		}
        		else{
        			break;
        		}

        	}
        	return numDec;
        }

        private int getNumRobotIncrease(){
        	int numInc = 0;
        	for (int i = 1; i < 4; i++){
        		if (robotHist[i] > robotHist[i-1]){
        			numInc++;
        		}
        		else{
        			break;
        		}

        	}
        	return numInc;
        }

        private int getNumRobotDecrease(){
        	int numDec = 0;
        	for (int i = 1; i < 4; i++){
        		if (robotHist[i] < robotHist[i-1]){
        			numDec++;
        		}
        		else{
        			break;
        		}

        	}
        	return numDec;
        }

	}

	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}
