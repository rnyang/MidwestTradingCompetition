
import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.*;

public class MIT3MathCase extends AbstractMathCase implements MathCase {		
		//parameters
		private IDB myDatabase;
		int numberOfPointsToGenerateModel;
		int numberOfIterationsForMost;
		int numberOfIterationsForFinal;
		int numberOfDataPointsToRemove;
		int timeToRebuild;
		int lengthOfLongRun;
		
		//maintain pnl
		int contracts = 0; //current position
        int pnlExcludingcontracts = 0; //pnl without marking current position to market
        int pnl = 0; //make a vector of historical pnl
        
        //current market and old data
        int currentPrice = 10000;        
        int currentSpread = 0;      
        int timeStep = 0;
        ArrayList<Integer> priceChanges = new ArrayList<Integer>(); //market price
        ArrayList<Integer> spreads = new ArrayList<Integer>(); //spread as defined in packet

        //model info
        int[] possiblePriceEmissions = {0, 1, -1, 3, -3, 5, -5, 10, -10};
        int[] possibleSpreadEmissions = {1, 3, 5};
        boolean isModelGenerated = false;
        
        //HMM parameters        
        double[][] transitions = new double[2][2]; //transition probabilities from i to j
        Map<Integer, Double>[] priceEmission = new Map[2]; //probabilities for price change, from state i
        Map<Integer, Double>[] spreadEmission = new Map[2]; //probabilities for spread, from state i
        double[][] alpha;
        double[][] beta;
        double[] initialProbabilities = new double[2];
        double observationProbability;  
            
        //HMM post-processing
        double[] expectedPriceChangeFromState = new double[2]; //expected price change in this state
        double[] expectedSpreadFromState = new double[2]; //expected spread in this state
        double[] longRunStateProbabilities = new double[2]; //probability of being in state after multiple timesteps
        double[] nextRoundStateProbabilities = new double[2]; //probability of being in this state next timestep
        double[] currentStateProbability = new double[2];
        
		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("numberOfDataPointsToRemove", "Number Of Points Used To Remove To Prevent NaN", "int", "50");
			setup.addVariable("numberOfPointsToGenerateModel", "Number Of Points Used To Generate The Model", "int", "100");
			setup.addVariable("numberOfIterationsForMost", "Number Of Iterations For Grid Search", "int", "10");
			setup.addVariable("numberOfIterationsForFinal", "Number Of Iterations For Selected Model", "int", "500");
			setup.addVariable("timeToRebuild", "Number Of Iterations to use model before rebuilding", "int", "10000");
			setup.addVariable("lengthOfLongRun", "The Multiplier of Price Changes to Model a Long Run Sequence.", "int", "50");
		}

		private void updatePNL() {
			pnl = pnlExcludingcontracts + contracts*currentPrice;
			
            log("My current position is: " + contracts);
            log("My current pnl is: " + pnl);
		}
		
		public void initializeAlgo(IDB database) {			
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			numberOfDataPointsToRemove = getIntVar("numberOfDataPointsToRemove"); 
			numberOfPointsToGenerateModel = getIntVar("numberOfPointsToGenerateModel"); 
			numberOfIterationsForMost = getIntVar("numberOfIterationsForMost"); 
			numberOfIterationsForFinal = getIntVar("numberOfIterationsForFinal"); 
			timeToRebuild = getIntVar("timeToRebuild"); 
			lengthOfLongRun = getIntVar("lengthOfLongRun");
			
			initializeModel(0.5, 0.5, 0.5, 0.5);
		}
		
		public void initializeModel(double trans00, double trans01, double trans10, double trans11) {
			transitions[0][0] = trans00;
			transitions[0][1] = trans01;
			transitions[1][0] = trans10;
			transitions[1][1] = trans11;

			priceEmission[0] = new HashMap<Integer, Double>();
			priceEmission[1] = new HashMap<Integer, Double>();
			for (int i = 0; i < possiblePriceEmissions.length; i++) {
				priceEmission[0].put(possiblePriceEmissions[i], 1.0 / 9.0);
				priceEmission[1].put(possiblePriceEmissions[i], 1.0 / 9.0);
			}
			
			spreadEmission[0] = new HashMap<Integer, Double>();
			spreadEmission[1] = new HashMap<Integer, Double>();
			for (int i = 0; i < possibleSpreadEmissions.length; i++) {
				spreadEmission[0].put(possibleSpreadEmissions[i], 1.0 / 3.0);
				spreadEmission[1].put(possibleSpreadEmissions[i], 1.0 / 3.0);
			}
			
			initialProbabilities[0] = 1;
			initialProbabilities[1] = 0;
		}
		
		private void iterativeRefinement() {
			alpha = new double[priceChanges.size()][2]; //(observation number, state)
			beta = new double[priceChanges.size()][2]; //(observation number, state)
			
			for (int i = 0; i < 2; i++)
				alpha[0][i] = initialProbabilities[i] * priceEmission[i].get(priceChanges.get(0)) * spreadEmission[i].get(spreads.get(0));
			
			for (int t = 0; t < (alpha.length - 1); t++) {
				for (int j = 0; j < 2; j++) {
					alpha[t+1][j] = 0;
					
					for (int i = 0; i < 2; i++) {
						alpha[t+1][j] += alpha[t][i] * transitions[i][j];
					}
					double value1 = priceEmission[j].get(priceChanges.get(t+1));
					double value2 = spreadEmission[j].get(spreads.get(t+1));
					alpha[t+1][j] *= value1 * value2;
				}
			}

			observationProbability = alpha[alpha.length - 1][0] + alpha[alpha.length - 1][1];					
			beta[beta.length - 1][0] = beta[beta.length - 1][1] = 1;
			
			for (int t = beta.length - 2; t >= 0; t--) {
				for (int i = 0; i < 2; i++) {
					beta[t][i] = 0;
					
					for (int j = 0; j < 2; j++)
						beta[t][i] += transitions[i][j] * priceEmission[j].get(priceChanges.get(t+1)) * spreadEmission[j].get(spreads.get(t+1)) * beta[t+1][j];				
				}	
			}
			
			//maybe do not allow this to be updated
			for (int i = 0; i < 2; i++) 
				initialProbabilities[i] = alpha[0][i] * beta[0][i] / observationProbability;
			
			for (int i = 0; i < 2; i++)
				for (int j = 0; j < 2; j++) {
					double numerator = 0.0;
					double denominator = 0.0;
					
					for (int t = 0; t < alpha.length - 1; t++) {
						numerator += alpha[t][i] * transitions[i][j] * priceEmission[j].get(priceChanges.get(t+1)) * spreadEmission[j].get(spreads.get(t+1)) * beta[t+1][j];
						denominator += alpha[t][i] * beta[t][i];
					}
					
					transitions[i][j] = (numerator / denominator) + 1e-8;
				}
			
			for (int j = 0; j < 2; j++) {
				for (int k = 0; k < possiblePriceEmissions.length; k++) {
					int thisPriceEmission = possiblePriceEmissions[k];
					double numerator = 0.0;
					double denominator = 0.0;
					
					for (int t = 0; t < alpha.length; t++) {
						double gamma = alpha[t][j]*beta[t][j];
						denominator += gamma;
						
						if (priceChanges.get(t).equals(thisPriceEmission))
							numerator += gamma;
					}
					
					priceEmission[j].put(thisPriceEmission, (numerator / denominator) + 1e-3);
				}

				for (int k = 0; k < possibleSpreadEmissions.length; k++) {
					int thisSpreadEmission = possibleSpreadEmissions[k];
					double numerator = 0.0;
					double denominator = 0.0;
					
					for (int t = 0; t < alpha.length; t++) {
						double gamma = alpha[t][j]*beta[t][j];
						denominator += gamma;
						
						if (spreads.get(t).equals(thisSpreadEmission))
							numerator += gamma;
					}
					
					spreadEmission[j].put(thisSpreadEmission, (numerator / denominator) + 1e-3);
				}
			}
			
		}
		
		private void printModel() {
			log("Observation Probability: " + observationProbability);
			log("Initial Probability: " + initialProbabilities[0] + " " + initialProbabilities[1]);
			log("Transitions: " + transitions[0][0] + " " + transitions[0][1] + " " + transitions[1][0] + " " + transitions[1][1]);
			log("PriceEmission[0]: " + priceEmission[0]);
			log("PriceEmission[1]: " + priceEmission[1]);
			log("SpreadEmission[0]: " + spreadEmission[0]);
			log("SpreadEmission[1]: " + spreadEmission[1]);			
		}
		
		//be careful of division by 0 and other ways of generating NaN
		private void generateModel() {
			isModelGenerated = false;
	        ArrayList<Model> candidateModels = new ArrayList<Model>();

			for (double a = 0; a <= 1.0001; a += 0.1)
				for (double b = 0; b <= 1.001; b += 0.1) {
					
					initializeModel(a, 1-a, b, 1-b);
					for (int i = 0; i < numberOfIterationsForMost; i++)
						iterativeRefinement();

					candidateModels.add(new Model(transitions, priceEmission, spreadEmission, alpha, beta, observationProbability, initialProbabilities));
				}

			Model bestModel = candidateModels.get(0);
			for (int i = 0; i < candidateModels.size(); i++)
				if (candidateModels.get(i).thisObservationProbability > bestModel.thisObservationProbability)
					bestModel = candidateModels.get(i);
			
			transitions = bestModel.thisTransitions;
			priceEmission = bestModel.thisPriceEmission;
			spreadEmission = bestModel.thisSpreadEmission;
			alpha = bestModel.thisAlpha;
			beta = bestModel.thisBeta;
			observationProbability = bestModel.thisObservationProbability;
			initialProbabilities = bestModel.thisInitialProbabilities;

			for (int i = 0; i < numberOfIterationsForFinal; i++)
				iterativeRefinement();
			
			printModel();
			isModelGenerated = true;
		}
		
		public void computeExpectedPriceSpreadChangesAndLongRun() {
	        expectedPriceChangeFromState = new double[2];
	        expectedSpreadFromState = new double[2];
	        longRunStateProbabilities = new double[2];
	        nextRoundStateProbabilities = new double[2];
	        currentStateProbability = new double[2];
	        
			for (int state = 0; state < 2; state++)
				currentStateProbability[state] = alpha[alpha.length - 1][state] * beta[beta.length - 1][state] / observationProbability;

			for (int previous = 0; previous < 2; previous++)
				for (int state = 0; state < 2; state++)
					nextRoundStateProbabilities[state] += transitions[previous][state] * currentStateProbability[previous];
			
			for (int state = 0; state < 2; state++) {
				
				for (int i = 0; i < possiblePriceEmissions.length; i++) {
					int thisPriceEmission = possiblePriceEmissions[i];
					expectedPriceChangeFromState[state] += priceEmission[state].get(thisPriceEmission) * thisPriceEmission;
				}

				for (int i = 0; i < possibleSpreadEmissions.length; i++) {
					int thisSpreadEmission = possibleSpreadEmissions[i];
					expectedSpreadFromState[state] += spreadEmission[state].get(thisSpreadEmission) * thisSpreadEmission;
				}
			}

			longRunStateProbabilities[0] = transitions[1][0] / (transitions[1][0] + transitions[0][1]);
			longRunStateProbabilities[1] = 1 - longRunStateProbabilities[0];
		}
		
		public void removeOldData() {
			numberOfDataPointsToRemove = Math.min(numberOfDataPointsToRemove, alpha.length - 1);
			
			for (int state = 0; state < 2; state++) 
				initialProbabilities[state] = alpha[numberOfDataPointsToRemove][state] * beta[numberOfDataPointsToRemove][state] / observationProbability;
			
	        ArrayList<Integer> newPriceChanges = new ArrayList<Integer>();
	        ArrayList<Integer> newSpreads = new ArrayList<Integer>();

	        for (int i = numberOfDataPointsToRemove; i < priceChanges.size(); i++) {
	        	newPriceChanges.add(priceChanges.get(i));
	        	newSpreads.add(spreads.get(i));
	        }
	        
	        priceChanges = newPriceChanges;
	        spreads = newSpreads;
			
			for (int i = 0; i < numberOfIterationsForMost; i++)
				iterativeRefinement();
		}
		
		public int newBidAsk(double bid, double ask) {
			log("Final program received a new bid of " + bid + ", and ask of " + ask);

			int integerBid = (int) (bid + 1e-6); //bid/ask are always integers
			int integerAsk = (int) (ask + 1e-6); 
			int oldPrice = currentPrice;
			
			currentPrice = (integerBid + integerAsk) / 2; //mid-market is always an integer
			currentSpread = (integerAsk - integerBid) / 2; 
			int currentPriceChange = currentPrice - oldPrice;
			
			priceChanges.add(currentPriceChange);
			spreads.add(currentSpread);
			timeStep++;
			
			log("The price changed by " + currentPriceChange + " and the spread is " + currentSpread);
			log("This is the " + timeStep + "th update");
			updatePNL();
			
			if (timeStep >= numberOfPointsToGenerateModel)
				if (((timeStep - numberOfPointsToGenerateModel) % timeToRebuild) == 0) {
				generateModel();
			}
			if (!isModelGenerated)
				return 0;
			
			//trade using the model
			for (int i = 0; i < numberOfIterationsForMost; i++)
				iterativeRefinement();

			if(observationProbability < 1e-250) {
				log(numberOfDataPointsToRemove + " points removed.");
				removeOldData();
				printModel();
			}
			
			computeExpectedPriceSpreadChangesAndLongRun(); 

			double expectedNextPrice = currentPrice;
			double expectedAfterNextPrice = currentPrice;
			double expectedLongRunPrice = currentPrice;
			double expectedNextSpread = 0.0;
			double expectedAfterNextSpread = 0.0;
			double expectedLongRunSpread = 0.0;
			
			for (int state = 0; state < 2; state++) {
				expectedNextPrice += currentStateProbability[state] * expectedPriceChangeFromState[state];
				expectedNextSpread += currentStateProbability[state] * expectedSpreadFromState[state];
				expectedAfterNextPrice += nextRoundStateProbabilities[state] * expectedPriceChangeFromState[state];
				expectedAfterNextSpread += nextRoundStateProbabilities[state] * expectedSpreadFromState[state];
				expectedLongRunPrice += lengthOfLongRun * expectedPriceChangeFromState[state] * longRunStateProbabilities[state]; //50 is a parameter for how long to forecast about long run 
				expectedLongRunSpread += expectedSpreadFromState[state] * longRunStateProbabilities[state];
			}
			
			double expectedLongRunBid = expectedLongRunPrice - expectedLongRunSpread;
			double expectedLongRunAsk = expectedLongRunPrice + expectedLongRunSpread;

			double expectedNextBid = expectedNextPrice - expectedNextSpread;
			double expectedNextAsk = expectedNextPrice + expectedNextSpread;

			double expectedAfterNextBid = expectedAfterNextPrice - expectedAfterNextSpread;
			double expectedAfterNextAsk = expectedAfterNextPrice + expectedAfterNextSpread;
			
			log("Observation Probability: " + observationProbability);
			log("Expected Next Bid: " + expectedNextBid);
			log("Expected Next Ask: " + expectedNextAsk);
			log("Expected AfterNext Bid: " + expectedAfterNextBid);
			log("Expected AfterNext Ask: " + expectedAfterNextAsk);
			log("Expected LongRun Bid: " + expectedLongRunBid);
			log("Expected LongRun Ask: " + expectedLongRunAsk);
			
			//become flat if model breaks
			if (Double.isNaN(expectedNextBid) || Double.isNaN(expectedNextAsk) || Double.isNaN(expectedAfterNextBid) || Double.isNaN(expectedAfterNextAsk) || Double.isNaN(expectedLongRunBid) || Double.isNaN(expectedLongRunAsk)) {
				log("The model has broken.");
				return -contracts; 
			}
			
			//Do Next Price First
			if (expectedNextBid > ask) //buy
				return (5 - contracts);
			if (expectedNextAsk < bid) //sell
				return -(contracts + 5);

			//Then Do After Next Price 
			if (expectedAfterNextBid > ask) //buy
				return (5 - contracts);
			if (expectedAfterNextAsk < bid) //sell
				return -(contracts + 5);
			
			//Then Long Run
			if (expectedLongRunBid > ask) //buy
				return (5 - contracts);
			if (expectedLongRunAsk < bid) //sell
				return -(contracts + 5);

			return 0;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);

			contracts += volume;
			pnlExcludingcontracts += -volume*fillPrice; 
			updatePNL();
		}

		public class Model {
	        public double[][] thisTransitions;
	        public Map<Integer, Double>[] thisPriceEmission; 
	        public Map<Integer, Double>[] thisSpreadEmission;
	        public double[][] thisAlpha;
	        public double[][] thisBeta;
	        public double thisObservationProbability;
	        public double[] thisInitialProbabilities;
	        
	        public Model (double[][] newTransitions, Map<Integer, Double>[] newPriceEmission, Map<Integer, Double>[] newSpreadEmission, double[][] newAlpha, double[][] newBeta, double newObservationProbability, double newInitialProbabilities[]){
	        	thisTransitions = newTransitions;
	        	thisPriceEmission = newPriceEmission;
	        	thisSpreadEmission = newSpreadEmission;
	        	thisAlpha = newAlpha;
	        	thisBeta = newBeta;
	        	thisObservationProbability = newObservationProbability;
	        	thisInitialProbabilities = newInitialProbabilities;
	        }
		}

	public MathCase getMathCaseImplementation() {
		return this;
	}
}
