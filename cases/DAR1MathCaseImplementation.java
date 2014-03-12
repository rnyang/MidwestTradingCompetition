
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * Baum-Welch seemed to overfit
 * Maybe a naive estimation would be better?
 * Assume up and down state...
 */

public class DAR1MathCaseImplementation extends AbstractMathCase implements MathCase {
	
		
		private IDB myDatabase;
		int factor;
        int contract = 0;
        double cash = 0, stock = 0, pnl = 0, price = 10000, spread = 0;
        int t = 0;
        ArrayList<Double> priceChanges = new ArrayList<Double>();
        ArrayList<Double> spreads = new ArrayList<Double>();
        
        double[][] TransitionMatrix = new double[2][2];
        double[][] EmissionMatrix = new double[2][9];
        
        List<Double> emissions = Arrays.asList(0.0, 1.0, -1.0, 3.0, -3.0, 5.0, -5.0, 10.0, -10.0);
        
        double expectedUp;
        double expectedDown;
        double averageSpread = 0;
        
        double up;
        double down;
        
        double tick = 0;
        
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
			t = t + 1;
			log("Time is: " + t);
			
			double lastPrice = price;
			price = (bid + ask) / 2;
			spread = price - bid;
			
			// Track price changes and spreads
			priceChanges.add(price - lastPrice);
			spreads.add(spread);
			
            // Receive new bid and ask
            // Input your HMM algorithm here to calculate how much to buy or sell
			if (t > 1 && t <= 100) {
				if (t == 100) train();
				return 0;
			}
			
			double expectedChange;
			double expectedIfUp;
			double expectedIfDown;
			
			int lastState;
			if (priceChanges.get(t - 1) >= 0) {
				lastState = 0;
			}
			else {
				lastState = 1;
			}
				
			
			if (lastState == 0) {
				expectedIfUp = TransitionMatrix[0][0] * expectedUp;
				expectedIfDown = TransitionMatrix[0][1] * expectedDown;
				expectedChange = expectedIfUp + expectedIfDown;
			}
			else {
				expectedIfUp = TransitionMatrix[1][0] * expectedUp;
				expectedIfDown = TransitionMatrix[1][1] * expectedDown;
				expectedChange = expectedIfUp + expectedIfDown;
			}
			
			log("Expected change: " + expectedChange);
			
			// market bias up
			if (expectedUp > -1 * expectedDown) {
				if (spread == 1 && expectedChange > 0)
					return 5;
				else if (expectedChange < -1 * spread - averageSpread)
					return -5;
			}
			// market bias down
			else {
				if (spread == 1 && expectedChange < 0)
					return -5;
				else if (expectedChange > 1 * spread + averageSpread)
					return 5;
			}	
            
            return 0;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            cash = cash - volume * fillPrice;
            updatePNL();
            log("My current position is: " + contract);
            log("My current profit is: " + pnl);
		}
		
		private void updatePNL() {
			stock = contract*price;
			pnl = cash + stock;
		}
		
		// Terrible code, but last minute fix since baum-welch didn't work
		private void train() {
			double upup = 0;
			double updown = 0;
			double downup = 0;
			double downdown = 0;
			
			for (int i = 0; i < priceChanges.size() - 1; i++) {
				if (priceChanges.get(i) < 0 && priceChanges.get(i + 1) < 0) {
					downdown++;
				}
				else if (priceChanges.get(i) < 0 && priceChanges.get(i + 1) >= 0) {
					downup++;
				}
				else if (priceChanges.get(i) >= 0 && priceChanges.get(i + 1) >= 0) {
					upup++;
				}
				else if (priceChanges.get(i) >= 0 && priceChanges.get(i + 1) < 0) {
					updown++;
				}
			}
			
			log("upup: " + upup);
			log("updown: " + updown);
			log("downup: " + downup);
			log("downdown: " + downdown);
			
			TransitionMatrix[0][0] = upup / (upup + updown);
			TransitionMatrix[0][1] = updown / (upup + updown);
			TransitionMatrix[1][0] = downup / (downup + downdown);
			TransitionMatrix[1][1] = downdown / (downup + downdown);
			
			double countUp = 0;
			double countDown = 0;
			for (int i = 0; i < priceChanges.size(); i++) {
				if (priceChanges.get(i) >= 0)
					countUp++;
				else
					countDown++;
				int index = emissions.indexOf(priceChanges.get(i));
				if (priceChanges.get(i) >= 0) {
					EmissionMatrix[0][index] += 1;
					up++;
				}
				else {
					EmissionMatrix[1][index] += 1;
					down++;
				}
			}
			
			for (int i = 0; i < 9; i++) {
				EmissionMatrix[0][i] *= (1.0 / countUp);
				EmissionMatrix[1][i] *= (1.0 / countDown);
			}

			expectedUp = 0;
			expectedDown = 0;
			for (int i = 0; i < 9; i++) {
				expectedUp += EmissionMatrix[0][i] * emissions.get(i);
				expectedDown += EmissionMatrix[1][i] * emissions.get(i);
			}
			
			for (int i = 0; i < spreads.size(); i++) {
				averageSpread = averageSpread + spreads.get(i);
			}
			averageSpread = averageSpread / 100;
			
			log("Average Spread: " + averageSpread);
			log("Price ups: " + up);
			log("Price downs: " + down);
		}

	public MathCase getMathCaseImplementation() {
		return this;
	}

}
