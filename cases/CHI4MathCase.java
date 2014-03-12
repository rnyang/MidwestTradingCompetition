import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a bare bones sample of a MathCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */
import java.lang.Math;

public class CHI4MathCase extends AbstractMathCase implements MathCase {
	
		
		private IDB myDatabase;
		int buyFactor;
		int sellFactor;
        int contract = 0;
        int time = 1;
        int i,j;
        int[] observedValues = new int[2000];
        double nextPriceChange;
        double nextSpread;
        double pnl = 0;
        double lastBuyPrice;
        double lastSellPrice;
        double shortTerm;
        double middleTerm;
        int timePassive = 0;
        //double longTerm;
        double expectedFinalPrice = 10000;
        int price;
        int takeProfit;
        double priceDeriv;
        double[] priceChangeArr = new double [2000];
        int finalBound;
       
        // initialize probabilities 
        double[][] probB = new double[2][9];
        private double[][] init_B( int t) {
        probB[0][0] = t*.1090;
        probB[0][1] = .1102;
        probB[0][2] = .1198;
        probB[0][3] = .1089;
        probB[0][4] = .1141;
        probB[0][5] = .1124;
        probB[0][6] = .1126;
        probB[0][7] = .1057;
        probB[0][8] = .1073;
        
        probB[1][0] = .1101;
        probB[1][1] = .1109;
        probB[1][2] = .1075;
        probB[1][3] = .1155;
        probB[1][4] = .1111;
        probB[1][5] = .1139;
        probB[1][6] = .1051;
        probB[1][7] = .1169;
        probB[1][8] = .1090;
        return probB;
        }
        
          
        double[][] probA = new double [2][2];
        private double[][] init_A(int t){
        probA[0][0] = t*.5009;
        probA[0][1] = .4991;
        
        probA[1][0] = .4086;
        probA[1][1] = .5014;
        return probA;
        }
   
        
        double [][] probS = new double [2][3];
        public double[][] init_S(int t){
        	probS[0][0] = t*.3331;
        	probS[0][1] = .3332;
        	probS[0][2] = .3327;
        	
        	probS[1][0] = .3340;
        	probS[1][1] = .3227;
        	probS[1][2] = .3433;
        	return probS;
        }
        double[][] probA_S = new double [2][2];
        private double[][] init_A_S(int t){
        probA_S[0][0] = t*.5009;
        probA_S[0][1] = .4991;
        
        probA_S[1][0] = .4086;
        probA_S[1][1] = .5014;
        return probA_S;
        }
        
        
        	
        
        int []observedChange = new int[2000];
  
        
        int []observedSpread = new int [2000];
        
        
        double[] pi = new double [2];
        
        double expectedNextPriceChange;
        
        
        double expectedNextSpreadChange = probS[0][0] + probS[1][0] + probS[0][1]*3 + probS[1][1]*3 
        + probS[0][2]*5 + probS[1][2]*5;
        
        
        //double threshold = 9970.0;
        

		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("buyFactor", "buy amount", "int", "1");
			setup.addVariable("sellFactor", "sell amount", "int", "1");
			setup.addVariable("nextPriceChange" , "expected next price" ,  "double", "0");
			setup.addVariable("nextSpread" , "expected nextSpread" ,  "double", "3");
			setup.addVariable("shortTerm" , "criteria for short-term buying/selling" ,  "double", ".3");
			setup.addVariable("middleTerm" , "criteria for middle-term buying/selling" ,  "double", ".2");
			setup.addVariable("takeProfit" , "satisfactory profit" ,  "int", "500");
			setup.addVariable("finalBound", "bound", "int", "50");
		}

		public double b_k(int oV, int state){
			switch (oV){
			case -10: return  probB[state][0];
			case -5: return probB[state][1];
			case -3: return probB[state][2];
			case -1:  return probB[state][3];
			case 0: return probB[state][4];
			case 1: return probB[state][5];
			case 3: return probB[state][6];
			case 5:  return probB[state][7];
			default: return probB[state][8];
			}
		}
		

		public double b_s(int ov, int state){
			
			switch (ov){
			case 1: return probS[state][0];
			case 3:  return probS[state][1];
			default: return probS[state][2];
			}
		}
		public int numToObs(int x){
			switch(x){
			case 0: return  -10;
			case 1: return -5;
			case 2: return-3;
			case 3: return  -1;
			case 4: return  0;
			case 5: return  1;
			case 6: return   3;
			case 7: return 5;
			default: return 10;
			} 
		}
		public int numToS(int x){
			switch(x){
			case 0: return 1;
			case 1: return 3;
			default: return 5;
			}
		}
		
		
		public void initializeAlgo(IDB database) {
			
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			buyFactor = getIntVar("buyFactor");
			sellFactor = getIntVar("sellFactor");
			nextPriceChange = getIntVar("nextPriceChange");
			nextSpread = getDoubleVar("nextSpread");
			shortTerm = getDoubleVar("shortTerm");
			middleTerm = getDoubleVar("middleTerm");
			takeProfit = getIntVar("takeProfit");
			finalBound = getIntVar("finalBound");
		}

		private double expectedNextPriceChange(int t){
			int maxIters = 100;
			int iters = 0;
			double[][] a = new double[t][2];
			double[][] b = new double[t][2];
			double[][][] g_ij = new double[t][2][2];
			double[][] g_i = new double[t][2];
			double[] c = new double[t];
			init_A(1);
			init_B(1);
			double denom = 0;
			double numer = 0;
			
			double oldlogprob = -1000;
			double logprob = 0;
			
			 // initialize probabilities
	       
			pi[0] = 1;
			pi[1] = 0;
			while (iters<=maxIters && logprob > oldlogprob ){
				oldlogprob = logprob;
			//The alpha[0][i] pass	
			c[0] = 0;
			int i;
			for (i=0; i <= 1; i++){
				a[0][i] =  pi[i] * b_k(observedChange[1],i);
				c[0] = c[0] + a[0][i];
			}
			//Scale the a[0][i]
			c[0] = 1/c[0];
			for( i = 0; i<=1; i++){
				a[0][i] = c[0]*a[0][i];
			}
			
			//compute a[k][i]
			int k;
			int j;
			for (k=1; k<=t-1; k++){
				c[k] = 0;
				for (i=0; i<=1; i++){
					a[k][i] =0;
					for(j = 0; j<=1; j++){
						a[k][i] = a[k][i] + a[k-1][j] * probA[j][i];
					}
					a[k][i] = a[k][i] * b_k(observedChange[k],i);
					c[k] = c[k] + a[k][i];
					}	
					//scale a[t][i]
					c[k] = 1/c[k];
					for (i=0; i<=1; i++){
						a[k][i] = c[k] * a[k][i];
					}
			}
					
			// The beta pass
			// scale b
			for (i = 0; i<=1; i++){
				b[t-1][i] = c[t-1];
				}
			// beta pass
			for (k = t-2; k>=0; k--){
				for (i=0; i<=1; i++){
					b[k][i] = 0;
					for (j = 0; j<=1; j++){
						b[k][i] = b[k][i] + probA[i][j] * b_k(observedChange[k+1], j)* b[k+1][j];
					}
					//scale b by same factor as a
					b[k][i] = b[k][i]*c[k];
				}
			}
			
			// compute g_ij and g_i
			for (k = 0; k<=t-2; k++){
				denom = 0;
				for (i = 0; i <= 1; i++){
					for (j = 0; j <= 1; j++){
						denom = denom + a[k][i]*probA[i][j]*b_k(observedChange[k+1], j)*b[k+1][j];
					}
				}
			
			
	
			
				for (i = 0; i<=1; i++){
					g_i[k][i] = 0;
					for (j = 0; j<=1; j++){
						g_ij[k][i][j] = (a[k][i]*probA[i][j]*b_k(observedChange[k+1],j)*b[k+1][j])/denom;
						g_i[k][i] = g_i[k][i] + g_ij[k][i][j];
					}
				}
			}
			
		
			 // Re-estimate probA, probB
			// Re-estimate probA
			for (i = 0; i<=1; i++){
				for (j = 0; j<=1; j++){
					numer = 0;
					denom = 0;
					for (k = 0; k<= t-2; k++){
						numer = numer + g_ij[k][i][j];
						denom = denom + g_i[k][i];
					}
					probA[i][j] = numer/denom;
				}
			}
			
			//Re-estimate probB
			for (i=0; i <= 1; i++){
				for (j = 0; j<= 8; j++){
					numer = 0;
					denom = 0;
						for (k = 0; k<= t-2; k++){
							if (observedChange[k] == numToObs(j)){
								numer = numer + g_i[k][i];
								}
							denom = denom + g_i[k][i];
							}	
						
					probB[i][j] = numer/denom;
				}
			}
			for (i = 0; i <= t-1; i++){
				logprob = logprob + Math.log(c[i]);
			}
			logprob = -logprob;
			
			iters = iters + 1;
			
				
			}
			
			return 
					(probA[0][0]*
					(probB[0][0]*-(10) + probB[0][1]*(-5) +  probB[0][2]*(-3) + probB[0][3]*(-1) + 
					 probB[0][4]*0 + probB[0][5] + probB[0][6]*3 +
					probB[0][7]*5 + probB[0][8]*10) +
					
					probA[0][1] * 
					(probB[1][0]*-(10) + probB[1][1]*(-5) +  probB[1][2]*(-3) + probB[1][3]*(-1) + 
					 probB[1][4]*0 + probB[1][5] + probB[1][6]*3 +
					probB[1][7]*5 + probB[1][8]*10) +
					
					probA[1][0] * 
					(probB[0][0]*-(10) + probB[0][1]*(-5) +  probB[0][2]*(-3) + probB[0][3]*(-1) + 
					 probB[0][4]*0 + probB[0][5] + probB[0][6]*3 +
					probB[0][7]*5 + probB[0][8]*10) +
					
					probA[1][1]*
					(probB[1][0]*-(10) + probB[1][1]*(-5) +  probB[1][2]*(-3) + probB[1][3]*(-1) + 
					 probB[1][4]*0 + probB[1][5] + probB[1][6]*3 +
					probB[1][7]*5 + probB[1][8]*10))/2;
					
			
	}
		
		private double expectedNextSpread(int t){
			int maxIters = 200;
			int iters = 0;
			double[][] a = new double[t][2];
			double[][] b = new double[t][2];
			double[][][] g_ij = new double[t][2][2];
			double[][] g_i = new double[t][2];
			double[] c = new double[t];
			
			init_S(1);
			init_A_S(1);
			double denom = 0;
			double numer = 0;
			
			double oldlogprob = -1000;
			double logprob = 0;
			
			 // initialize probabilities
	       
			pi[0] = 1;
			pi[1] = 0;
			while (iters<=maxIters && logprob > oldlogprob ){
				oldlogprob = logprob;
				c[0] = 0;
				int i;
				for (i=0; i <= 1; i++){
					a[0][i] =  pi[i] * b_s(observedSpread[1],i);
					c[0] = c[0] + a[0][i];
				}
				//Scale the a[0][i]
				c[0] = 1/c[0];
				for( i = 0; i<=1; i++){
					a[0][i] = c[0]*a[0][i];
				}
				//compute a[k][i]
				int k;
				int j;
				for (k=1; k<=t-1; k++){
					c[k] = 0;
					for (i=0; i<=1; i++){
						a[k][i] =0;
						for(j = 0; j<=1; j++){
							a[k][i] = a[k][i] + a[k-1][j] * probA_S[j][i];
						}
						a[k][i] = a[k][i] * b_s(observedSpread[k],i);
						c[k] = c[k] + a[k][i];
						}	
						//scale a[t][i]
						c[k] = 1/c[k];
						for (i=0; i<=1; i++){
							a[k][i] = c[k] * a[k][i];
						}
				}
				// The beta pass
				// scale b
				for (i = 0; i<=1; i++){
					b[t-1][i] = c[t-1];
					}
				// beta pass
				for (k = t-2; k>=0; k--){
					for (i=0; i<=1; i++){
						b[k][i] = 0;
						for (j = 0; j<=1; j++){
							b[k][i] = b[k][i] + probA_S[i][j] * b_s(observedSpread[k+1], j)* b[k+1][j];
						}
						//scale b by same factor as a
						b[k][i] = b[k][i]*c[k];
					}
				}
				// compute g_ij and g_i
				for (k = 0; k<=t-2; k++){
					denom = 0;
					for (i = 0; i <= 1; i++){
						for (j = 0; j <= 1; j++){
							denom = denom + a[k][i]*probA_S[i][j]*b_s(observedSpread[k+1], j)*b[k+1][j];
						}
					}
				
				
		
				
					for (i = 0; i<=1; i++){
						g_i[k][i] = 0;
						for (j = 0; j<=1; j++){
							g_ij[k][i][j] = (a[k][i]*probA_S[i][j]*b_s(observedSpread[k+1],j)*b[k+1][j])/denom;
							g_i[k][i] = g_i[k][i] + g_ij[k][i][j];
						}
					}
				}
				 // Re-estimate probA_S, probS
				// Re-estimate probA_S
				for (i = 0; i<=1; i++){
					for (j = 0; j<=1; j++){
						numer = 0;
						denom = 0;
						for (k = 0; k<= t-2; k++){
							numer = numer + g_ij[k][i][j];
							denom = denom + g_i[k][i];
						}
						probA_S[i][j] = numer/denom;
					}
				}
				//Re-estimate probS
				for (i=0; i <= 1; i++){
					for (j = 0; j<= 2; j++){
						numer = 0;
						denom = 0;
							for (k = 0; k<= t-2; k++){
								if (observedSpread[k] == numToS(j)){
									numer = numer + g_i[k][i];
									}
								denom = denom + g_i[k][i];
								}
						probS[i][j] = numer/denom;
					}
				}
			
				for (i = 0; i <= t-1; i++){
					logprob = logprob + Math.log(c[i]);
				}
				logprob = -logprob;
				
				iters = iters + 1;
			}
			return 
					(probA_S[0][0]* (probS[0][0] + probS[0][1]* 3 + probS[0][2] * 5) +
					probA_S[0][1]* (probS[1][0] + probS[1][1]* 3 + probS[1][2] * 5) +
					probA_S[1][0]* (probS[0][0] + probS[0][1]* 3 + probS[0][2] * 5) +
					probA_S[1][1]* (probS[1][0] + probS[1][1]* 3 + probS[1][2] * 5))/2;
		}
		
		public int newBidAsk(double bid , double ask) {
			log("I received a new bid of " + bid + ", and ask of "
			+ ask + " with " + time + " and " + nextPriceChange + " plus " + (expectedFinalPrice + 50) );
			
            // Receive new bid and ask
            // Input your HMM algorithm here to calculate how much to buy or sell
			time +=1;
			observedValues[time] = (int) (bid+ask)/2;
			observedValues[0] = 100000;
			observedChange[time] = observedValues[time]-observedValues[time-1];
			observedChange[0] = 0;
			observedSpread[time] = (int) (ask-bid) / 2;
			int price = (int) (ask+bid) / 2;
			nextPriceChange = expectedNextPriceChange(time);
			//nextSpread = expectedNextSpread(time);
			if (time<100){
				return 0;
			}
			if (pnl + contract * price > takeProfit)
				return -contract;
			if (time >= 100 && time <=300){
				expectedFinalPrice = (1000 - 550) * nextPriceChange + price;
					if (ask - bid  != 10){
						if (Math.abs(contract) != 5){
						if (nextPriceChange > shortTerm){
							return buyFactor;
						}
						if (nextPriceChange < -shortTerm){
							return -sellFactor;
						}
						else {
							timePassive+=1;
							return 0;
						}
					}
					if (contract == -5){
						if (nextPriceChange >0){
							timePassive = 0;
							return buyFactor;
						}
							else {
							timePassive+=1;
							return 0;
						}
					}
					else{
					 if (nextPriceChange <0){
						 timePassive = 0;
						 return -sellFactor;
					 }
					 else {
						 timePassive+=1;
						 return 0;
					 }
					}
				}
					else {
						timePassive+=1;
						return 0;
					}
			}
			if (time >= 301 && time <= 750){
				expectedFinalPrice = (1000 - 750) * nextPriceChange + price;
				if (ask - bid != 10){
				if (Math.abs(contract) != 5){
					if (nextPriceChange > middleTerm){  
						return  buyFactor;
					}
					if (nextPriceChange < - middleTerm){
						return -sellFactor;
					}
					else {
						timePassive+=1;
						return 0;
					}
				}
				if (contract == 5){
					if (nextPriceChange < 0){
						timePassive = 0;
						return -sellFactor;
					}
					else {
						timePassive+=1;
						return 0;
					}
				}
				else{
					if (nextPriceChange > 0){
						timePassive = 0;
						return buyFactor;
					}
					else {
						timePassive+=1;
						return 0;
					}
					}
				}
				else {
					timePassive+=1;
					return 0;
				}
			}
			else {
				if (Math.abs(contract) != 5){
					if (nextPriceChange > 0){
						if (price < expectedFinalPrice)
							return buyFactor;
						else 
							return 0;
					}
					else{ 
						if (price > expectedFinalPrice)
							return -sellFactor;
						else 
							return 0;
				}
				}
				if (contract == 5){
					if (bid > expectedFinalPrice + finalBound){
						return -sellFactor* 5;
					}
					else 
						return 0;
				}
				else {
					if (ask < expectedFinalPrice - finalBound){
						return buyFactor * 5;
					}
					else 
						return 0;
				}
			}
		}
		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice
					+ " at " + time);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            log("My current position is: " + contract);
            pnl = pnl - volume*fillPrice;
            log("My current pnl is " + pnl);
		}
         
		
		
	public MathCase getMathCaseImplementation() {
		return this;  
	}
}



