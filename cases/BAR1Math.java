import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import java.util.ArrayList;

/*
 * Math Algo Implementation
 * Strategy 8
 * 
 */

public class BAR1Math extends AbstractMathCase implements MathCase {
	
	int factor;
    
    // Arraylist used to store the stock price
    public ArrayList<Double> priceStore = new ArrayList<Double>(); // Store all historical mid-prices
    public ArrayList<Integer> e1Sequence = new ArrayList<Integer>(); // Store price changes
    public ArrayList<Integer> e2Sequence = new ArrayList<Integer>(); // Store spreads
    
    private int time = 0;
    double priceChange;
    double spread;
    
    // Implementation of Differential Evolution (for faster convergence and increased likelihood of global maximum)
    DifferentialEvolver priceDE = new DifferentialEvolver(0.8, 0.9, 220, 9); // 9 -> amount of emissions
    DifferentialEvolver spreadDE = new DifferentialEvolver(0.8, 0.9, 220, 3); // 3 -> amount of emissions
    
    // HMMs trained by Baum Welch
    HMModel priceHMM = new HMModel(9); // For price changes 
    HMModel spreadHMM = new HMModel(3); // For spreads
    
    // P&L Tracker
    PLTracker pl;
    
    
    
    // Registers a variable with the system.
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}
	
	// Initialize the algorithm
	public void initializeAlgo(IDB database) {
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}

	
	public int newBidAsk(double bid, double ask) {
		// Receive new bid and ask
		log("I received a new bid of " + bid + ", and ask of " + ask);
        
		// Update time
		time++;
		
		// Store the mid-price
        priceStore.add( (bid + ask)/2 );
        int trade = 0; // Used to determine trade size
        
        // Initialize PnL tracker
        if(time == 1) pl = new PLTracker(bid, ask);
        
        // Start storing emissions
		if(time > 1){
			priceChange = (priceStore.get(time-1) - priceStore.get(time-2));
			spread = (ask - bid) / 2;
			
			// Record price changes and spread
			e1Sequence.add(identifyE1(priceChange));
			e2Sequence.add(identifyE2(spread));
			
			// Update P&L
			pl.updateA(bid, ask);
			
			// Output information
			log( Integer.toString(e1Sequence.size()) );
			log("e1:" + priceChange + ";e2:" + spread);
			log("P&L: " + Double.toString(pl.pl));
		}
		
		int e1Max, e2Max; // Used to find best HMMs among the population in the DE 
		double ex1, sp1; // Used to output next expected price change and spread 
		
		if(time > 100 && time < 200){
			// Evolve the HMM populations
			priceDE.evolveAll(e1Sequence);
			spreadDE.evolveAll(e2Sequence);
			
			// Find the index of the best HMMs among the population in the DE
			e1Max = priceDE.getMax();
			e2Max = spreadDE.getMax();
			ex1 = calcExpected(0, priceDE.hmmArray[e1Max].nextProb, priceDE.hmmArray[e1Max]);
			sp1 = calcExpected(1, spreadDE.hmmArray[e2Max].nextProb, spreadDE.hmmArray[e2Max]);
			
			// Output information
			log( "Price Change: " + ex1 + ";Spread: " + sp1);
			log( "Position: " + pl.position);
			
			// Decide trading position
			trade = tradeStrategy(pl.position, spread, 2.8, 4, priceDE.hmmArray[e1Max], spreadDE.hmmArray[e2Max]);
		}
		
		if(time == 200){
			// Find the index of the best HMMs among the populationin the DE
			e1Max = priceDE.getMax();
			e2Max = spreadDE.getMax();
			// Initialize price and spread HMMs with the best HMMs from population
			priceHMM = priceDE.hmmArray[e1Max];
			spreadHMM = spreadDE.hmmArray[e2Max];
		}
		if(time >= 200){
			// Update HMM parameters w. Baum-Welch
			priceHMM.updateAll(e1Sequence);
			spreadHMM.updateAll(e2Sequence);
			ex1 = calcExpected(0, priceHMM.nextProb, priceHMM);
			sp1 = calcExpected(1, spreadHMM.nextProb, spreadHMM);
			
			// Output information
			log( "Price Change: " + ex1 + ";Spread: " + sp1);
			log( "Position: " + pl.position);
			
			// Decide trading position
			trade = tradeStrategy(pl.position, spread, 2.8, 4, priceHMM, spreadHMM);
		}
		
		return trade;
	}

	// Called when an order is filled
	public void orderFilled(int volume, double fillPrice) {
		
		// Track the PnL
		pl.updateB(volume, fillPrice);
		
		// Output information
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}
	
	// Decides how much to trade; pThres -> threshold for prices, sThres -> threshold for spreads
	public int tradeStrategy(int position, double spread, double pThres, double sThres, HMModel priceHMM, HMModel spreadHMM){
		int trade = 0, tradeLimit = 5;
		
		// Expected price change in one period and in two period
		double ex1PriceChange = 0, ex2PriceChange = 0;
		// Expected spread in one period and in two periods
		double ex1Spread = 0, ex2Spread = 0;
		
		ex1PriceChange = calcExpected(0, priceHMM.nextProb, priceHMM);
		ex2PriceChange = calcExpected(0, priceHMM.twoProb, priceHMM);
		ex1Spread = calcExpected(1, spreadHMM.nextProb, spreadHMM);
		ex2Spread = calcExpected(1, spreadHMM.twoProb, spreadHMM);
		
		// Finds out whether to long or short
		int direction;
		if(ex1PriceChange < 0) direction = -1;
		else direction = 1;
		
		// Trade strategy
		if(position == 0){
			if(Math.abs(ex1PriceChange) > pThres && ex1Spread < sThres){
				if(spread == 1) trade = 5*direction;
				else if(spread == 3){
					if((ex2PriceChange*direction) > 0 && ex2Spread < sThres) trade = 3*direction;
				}
			}
			else trade = 0;
		}
		else{
			if(Math.abs(ex1PriceChange) > pThres && ex1Spread < sThres){
				if(spread == 1 && (direction*position) < 0) trade = direction*(5 + Math.abs(position));
				else if(spread == 1 && Math.abs(position) < tradeLimit) trade = direction*(tradeLimit - Math.abs(position));
			}
			else if((direction*position) < 0 && Math.abs(ex1PriceChange) > .5 && ex1Spread < sThres) trade = -1*position;
			else trade = 0;
		}
		
		return trade;
	}
	
	// Generate the expected value of next price change; eID is for ID of emissions, p are prob. for states
	public double calcExpected(int eID, double[] p, HMModel hmm){
		double ex = 0, sum = 0;
		double[] e;
		
		if(eID == 0) e = new double[] {-10, -5, -3, -1, 0, 1, 3, 5, 10};
		else e = new double[] {1, 3, 5};
		
		for(int k = 0; k < hmm.numEmissions; k++){
			for(int i = 0; i < hmm.numStates; i++){
				sum = sum + (p[i] * hmm.emissionMatrix[i][k]);
			}
			ex = ex + (e[k] * sum);
			sum = 0;
		}
		
		return ex;
	}
	
	// Categorize the price change emissions
	public int identifyE1(double e){
		int identity;
		
		if( e == 0) identity = 4;
		if( e > 0){
			if(e == 1) identity = 5;
			else if(e == 3) identity = 6;
			else if(e == 5) identity = 7;
			else identity = 8;
		}
		else{
			if(e == -1) identity = 3;
			else if(e == -3) identity = 2;
			else if(e == -5) identity = 1;
			else identity = 0;
		}
		
		return identity;
	}
		
	// Categorize the spread emissions
	public int identifyE2(double e){
		int identity;
		
		if(e == 1) identity = 0;
		else if(e == 3) identity = 1;
		else identity = 2;
		
		return identity;
	}

	public MathCase getMathCaseImplementation() {
		return this;
	}

}