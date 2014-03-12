
import org.chicago.cases.AbstractMathCase;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a barebones sample of a MathCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class CHI5MathCaseImplementation extends AbstractMathCase implements MathCase {
	
		public static final double NOT_FOUND = 10.0;
		public static final int UNINIT = 100;
		public static final int RESET = 101;
		private IDB myDatabase;
		int factor;
        int contract = 0;
        double profit = 0;
        public static int[] possibleE1 = {-10, -5, -3, -1, 0, 1, 3, 5, 10};
        public static int[] possibleE2 = {1, 3, 5};
		int[] e2s = new int[12000];
		int[] e1s = new int[12000];
		int[] m_bars = new int[12000];
		public static int[] modes = {UNINIT, UNINIT, UNINIT, UNINIT, UNINIT, UNINIT};
		public static int[] modes2 = {UNINIT, UNINIT, UNINIT, UNINIT, UNINIT, UNINIT};
		public static int[] weights = {1, 1, 1, 1, 1, 1};
		public static int[] weights2 = {1, 1, 1, 1, 1, 1};
		int period = 0;
		public static int num_changes = 0;

		public static double predictPeriods(int[] history, int[] vals, int forecast){
			int num_finds = 0;
			int sum = 0;
			int[] num_vals = {0, 0, 0, 0, 0, 0, 0, 0, 0};
			for (int i = 0; i < (num_changes - vals.length - (forecast - 1)); i++){
				boolean success = true;
				for (int j = 0; j < vals.length; j++){
					if (history[i + j] != vals[j]){ 
						success = false;
						break;
					}
				}
				if (success){
					num_finds++;
					System.out.println("searching for " + vals[vals.length - 1] + " forecast " + forecast + " next val " 
							+ history[i + vals.length + (forecast - 1)]);
					sum += history[i + vals.length + (forecast - 1)];
					switch(history[i + vals.length + (forecast - 1)]){
					case -10: num_vals[0]++;
						break;
					case -5: num_vals[1]++;
						break;
					case -3: num_vals[2]++;
						break;
					case -1: num_vals[3]++;
						break;
					case 0: num_vals[4]++;
						break;
					case 1: num_vals[5]++;
						break;
					case 3: num_vals[6]++;
						break;
					case 5: num_vals[7]++;
						break;
					case 10: num_vals[8]++;
						break;
					default: break;
					}
				}
			}
			if (num_finds == 0) { return NOT_FOUND; }
			
			if (modes[vals.length - 1] == UNINIT) { 
				weights[vals.length - 1] = max(weights);
			}
			if (forecast == 1){
				modes[vals.length - 1] = possibleE1[whichMax(num_vals)];
			}
			return (sum + 0.0) / num_finds;
		}
		
		public static double predictPeriods2(int[] history, int[] vals, int forecast){
			int num_finds = 0;
			int sum = 0;
			int[] num_vals = {0, 0, 0};
			for (int i = 0; i < (num_changes - vals.length - (forecast - 1)); i++){
				boolean success = true;
				for (int j = 0; j < vals.length; j++){
					if (history[i + j] != vals[j]){ 
						success = false;
						break;
					}
				}
				if (success){
					num_finds++;
					sum += history[i + vals.length + (forecast - 1)];
					switch(history[i + vals.length + (forecast - 1)]){
					case 1: num_vals[0]++;
						break;
					case 3: num_vals[1]++;
						break;
					case 5: num_vals[2]++;
						break;
					default: break;
					}
				}
			}
			if (num_finds == 0) { return NOT_FOUND; }
			
			if (modes2[vals.length - 1] == UNINIT) { 
				weights2[vals.length - 1] = max(weights2);
			}
			
			if (forecast == 1){
				modes2[vals.length - 1] = (2 * whichMax(num_vals)) + 1;
			}
			return (sum + 0.0) / num_finds;
		}
			
		public static int whichMax(int[] a){
			int max = 0;
			int which = 0;
			for (int i = 0; i < a.length; i++){
				if (a[i] > max){
					max = a[i];
					which = i;
				}
			}
			return which;
			
		}
		
		public static int max(int[] a){
			int max = 0;
			for (int i = 0; i < a.length; i++){
				if (a[i] > max){
					max = a[i];
				}
			}
			return max;
		}
			
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
			System.out.println("I received a new bid of " + bid + ", and ask of " + ask + " period " + (period + 1));
			
			int m_bar = (int) ((bid + ask) / 2);
			//System.out.println("m_bar: " + m_bar);
			int e2 = (int)((ask - bid) / 2);
			//System.out.println("e2: " + e2);
			int e1 = 0;
			if (period != 0){ e1 = m_bar - m_bars[period - 1]; }
			
			//System.out.println("e1: " + e1);
			
			m_bars[period] = m_bar;
			period++;
			
			if (e1 != 0){
				System.out.println("\n\n\n\n\n\n\ne1: " + e1 + " period " + period);
				e1s[num_changes] = e1;
				e2s[num_changes] = e2;
				num_changes++;
			
				for (int i = 0; i < modes.length; i++){
					if (modes[i] == e1){
						weights[i]++;
					}
					if (modes[i] != UNINIT) {
						modes[i] = RESET;
					}
					if(modes2[i] == e2){
						weights2[i]++;
					}
					if (modes2[i] != UNINIT){
						modes2[i] = RESET;
					}
				}
				
				double[] exValsP = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
				
				int i;
				for (i = 1; i < 10; i++){
					double[] exValsN = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
					int j;
					for (j = 1; j < 7; j++){
						int[] vals = new int[j];
						for (int k = j - 1; k >= 0; k--){
							vals[j - 1 - k] = e1s[num_changes - 1 - k];
						}
						double exValueJ = predictPeriods(e1s, vals, i);
						if (exValueJ == NOT_FOUND){
							break;
						}
						exValsN[j - 1] = exValueJ;
					}
					double sum = 0.0;
					int k;
					for (k = 0; k < j - 1; k++){
						sum += weights[k];
					}
					
					double discount = 1;
					for (k = 1; k < i; k++){
						discount *= (9 / 10);
					}
					
					for (k = 0; k < j - 1; k++){
						//System.out.println("expected value " + k + " periods: " + exValsN[k]);
						exValsP[i - 1] += discount * ((weights[k] / sum) * exValsN[k]);
					}
					
				}
				
				double[] exVals2P = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
				
				for (i = 1; i < 10; i ++){
					double[] exVals2N = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
					int j;
					for (j = 1; j < 7; j++){
						int[] vals = new int[j];
						for (int k = j - 1; k >= 0; k--){
							vals[j - 1 - k] = e2s[num_changes - 1 - k];
						}
						double exValueJ = predictPeriods2(e2s, vals, i);
						if (exValueJ == NOT_FOUND){
							break;
						}
						exVals2N[j - 1] = exValueJ;
					}
					double sum = 0.0;
					int k;
					for (k = 0; k < j - 1; k++){
						sum += weights2[k];
					}
					
					for (k = 0; k < j - 1; k++){
						exVals2P[i - 1] += ((weights2[k] / sum) * exVals2N[k]);
					}
					
				}
				
				if (period < 400) { return 0; }
				
				double exM = m_bar;
				System.out.println("period " + period + " current values " + e1 + " " + e2 + " " + m_bar);
				int margin = 0;
				for (i = 1; i < 10; i++){
					exM += exValsP[i - 1];
					double exBuy = exM - exVals2P[i - 1];
					//System.out.println("expected buy price at time " + i + ": " + exBuy);
					double exSell = exM + exVals2P[i - 1];
					//System.out.println("expected sell price at time " + i + ": " + exSell);
					System.out.println("expected sell: " + exSell + " expected buy " + exBuy + " period " + period + " + " + i);
					if (exBuy > ask){
						margin = (int)(exBuy - ask);
						if (margin + contract > 5 && contract >= 0){ return 5 - contract; }
						else if (margin > 5){ return 5;	}
						return margin; 
					}
					else if (exSell < bid){
						margin = (int)(exSell - bid);
						if (margin + contract < -5 && contract <= 0){ return -5 - contract; }
						else if (margin < -5)
						return margin;
					}
				}
			}
			
			return 0;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            contract = contract + volume;
            profit += -1 * (fillPrice * volume);
            log("My current position is: " + contract);
            log("Profit: " + profit);

		}


	public MathCase getMathCaseImplementation() {
		return this;
	}
	
	public static void main(String[] args){
		CHI5MathCaseImplementation test = new CHI5MathCaseImplementation();
		//System.out.println(test.newBidAsk(997, 1003));
		String csvFile = "mathcase.csv";
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		try {
			 
			br = new BufferedReader(new FileReader(csvFile));
			int i = 0;
			while ((line = br.readLine()) != null && i < 12000) {
	 
			        // use comma as separator
				String[] dat = line.split(cvsSplitBy);
	 
				double bid = Double.parseDouble(dat[4]);
				double ask = Double.parseDouble(dat[6]);
				
				int oldContract = test.contract;
				test.contract += test.newBidAsk(bid, ask);
				if(test.contract != oldContract) {
					System.out.println("contract: " + test.contract);
				}
				i++;
	 
			}
			
			System.out.println(test.contract);
	 
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}