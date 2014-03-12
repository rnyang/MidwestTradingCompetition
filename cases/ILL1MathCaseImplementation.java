
import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.ArrayList;

/*
 * This is a barebones sample of a MathCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class ILL1MathCaseImplementation extends AbstractMathCase implements MathCase {


        private final int MAX_POSITION = 5;
        private final int MIN_POSITION = -5;
        private boolean positionOpen = false;
        private double PnL = 0.0;
        private double lastGSI = 0.0;

        private IDB myDatabase;
		int factor;
        private int[] last5Incs = new int[5];
        private int totalTicks = 0;
        private int position = 0;

        private ArrayList<Integer> spreads = new ArrayList<Integer>();
        private ArrayList<Integer> increments = new ArrayList<Integer>();
        private ArrayList<Integer> midPrices = new ArrayList<Integer>();


    // HMM PARAMS *****************************************************************************
    private double[] stateInitProb = {.5, .5};   // Initial state probabilities


    private double[][] trans = {{.5, .5}, {.5, .5}};    // Transition probabilities from S1 to S2

    private double[][] incProb = {{.1, .1, .1, .1, .1, .1, .1, .1, .2},
            {.1, .1, .1, .1, .1, .1, .1, .1, .2}};

    private double[][] spreadProb = {{.33, .33, .34}, {.33, .33, .34}};;  // PMF for emission1
    // ****************************************************************************************

    // HMM BAUM WELCH PARAMS ******************************************************************
    // Global variables used in the calibration
    public int numTicks         = 1200;
    public double[] scale       = new double[numTicks];            // Scaling variable
    public double[][] alpha     = new double[numTicks][2];         // Fwd vars
    public double[][] beta      = new double[numTicks][2];         // Bwd vars
    public double[][][] ksi     = new double[numTicks][2][2];      // Transition-type probs
    public double[][] gamma     = new double[numTicks][2];
    public double[] diff		 = new double[30];					// An array of differences

    // ****************************************************************************************

    /**
     * First emission
     *
     * @param k The spread that we receive.
     * @return The index in the vector that we use for the probabilities.
     */
    private int emission1(int k) {
        int index;
        switch (k) {
            case -10:
                index = 0;
                break;
            case -5:
                index = 1;
                break;
            case -3:
                index = 2;
                break;
            case -1:
                index = 3;
                break;
            case 0:
                index = 4;
                break;
            case 1:
                index = 5;
                break;
            case 3:
                index = 6;
                break;
            case 5:
                index = 7;
                break;
            case 10:
                index = 8;
                break;
            default:
                index = -1;
        }
        return index;
    }

    /**
     *
     * @param k The spread that we receive.
     * @return The index in the vector that we use for probabilities.
     */
    private int emission2(int k) {
        int index;
        switch (k) {
            case 1:
                index = 0;
                break;
            case 3:
                index = 1;
                break;
            case 5:
                index = 2;
                break;
            default:
                index = -1;
        }
        return index;
    }


    /*  Function to edit global variables for forward probabilities up to time n
    *
    */
    private void fwdProbs(int n){
        // Fill initial element alpha[0][0] and alpha[0][1]
        scale[0]            = 0;
        for (int state=0;state<2; ++state) {
            alpha[0][state] = stateInitProb[state]  *incProb[state][emission1(increments.get(0))]
                    *spreadProb[state][emission2(spreads.get(0))];        // Fill in initial values
            scale[0] += alpha[0][state];
        }
        alpha[0][0] = alpha[0][0]/scale[0];
        alpha[0][1] = alpha[0][1]/scale[0];

        // Fill out alphas for remaining data in calibration window
        for (int t=1; t<n; ++t) {
            scale[t] = 0;
            for (int state1 = 0; state1 < 2; ++state1) {
                alpha[t][state1] = 0;
                for (int state2 = 0; state2 < 2; ++state2) {
                    alpha[t][state1] += incProb[state1][emission1(increments.get(t-1))]
                            *spreadProb[state1][emission2(spreads.get(t-1))]
                            *alpha[t-1][state2]*trans[state2][state1];
                }
                scale[t] += alpha[t][state1];	// Calculate the scale at ctr
            }
            // Scale alphas at ctr
            alpha[t][0] /=  scale[t];
            alpha[t][1] /=  scale[t];

            /***************************************************************
             * TESTING
             *
             System.out.println("SCALE[" + t + "] is " + scale[t]);
             System.out.println("ALPHA[" + t + "][0] is " + alpha[t][0]);
             System.out.println("ALPHA[" + t + "][1] is " + alpha[t][1]);

             if(t%100 == 0)
             new java.util.Scanner(System.in).nextLine();
             /***************************************************************/
        }
    }

    /*  Function to edit global variables for backward probabilities up to time n
    *
    */
    private void bwdProbs(int n){
        // Fill initial (end) elements beta[endIndex][0] and beta[endIndex][1]
        for(int state = 0; state<2; ++state)
            beta[n-1][state] = 1/scale[0];

        // Fill bwd variables for every time before this
        for (int t=n-2; t>0; --t) {
            for (int state1 = 0; state1 < 2; ++state1) {
                beta[t][state1] = 0;
                for (int state2 = 0; state2 < 2; ++state2) {
                    beta[t][state1] += beta[t+1][state2]*trans[state1][state2]
                            *incProb[state2][emission1(increments.get(t+1))]
                            *spreadProb[state2][emission2(spreads.get(t+1))];
                }
                beta[t][state1] /= scale[t];
            }

            /***************************************************************
             * TESTING
             *
             System.out.println("SCALE[" + t + "] is " + scale[t]);
             System.out.println("BETA[" + t + "][0] is " + beta[t][0]);
             System.out.println("BETA[" + t + "][1] is " + beta[t][1]);

             if(t%100 == 0)
             new java.util.Scanner(System.in).nextLine();
             /***************************************************************/
        }
    }

    /*  Function to edit global variables ksi up to time n
    *
    */
    private void fillKsi(int n){
        for (int t = 0; t<n; ++t) {
            int sum = 0;
            for(int state1 = 0; state1 < 2; ++state1){
                for (int state2 = 0; state2 < 2; ++state2) {
                    ksi[t][state1][state2] = alpha[t][state1]*trans[state1][state2]*beta[t+1][state2]
                            *incProb[state2][emission1(increments.get(t+1))]
                            *spreadProb[state2][emission2(spreads.get(t+1))];

                    /***************************************************************
                     * TESTING
                     *
                     if(t < 10){
                     System.out.println("alpha[" + t + "]["+state1+"] is " + alpha[t][state1]);
                     System.out.println("beta[" + (t+1) + "]["+state2+"] is " + beta[t+1][state2]);
                     System.out.println("trans[" + state1 + "]["+state2+"] is " + trans[state1][state2]);
                     System.out.println("inc prob is " + incProb[state2][emission1(increments.get(t+1))]);
                     System.out.println("spread prob is " + spreadProb[state2][emission2(spreads.get(t+1))]);
                     System.out.println("KSI[" + t + "]["+state1 + "][" + state2 + "] is " + ksi[t][state1][state2]);
                     new java.util.Scanner(System.in).nextLine();
                     }
                     /***************************************************************/


                    sum += ksi[t][state1][state2];
                }
            }


            /***************************************************************
             * TESTING
             *
             System.out.println("SCALE[" + t + "] is " + sum);
             System.out.println("KSI[" + t + "][0][0] is " + ksi[t][0][0]);
             System.out.println("KSI[" + t + "][0][1] is " + ksi[t][0][1]);

             System.out.println("---------------------------------");
             System.out.println("ALPHA[" + t + "][1] is " + alpha[t][1]);
             System.out.println("BETA[" + (t+1) + "][0] is " + beta[t+1][0]);
             System.out.println("BETA[" + (t+1) + "][1] is " + beta[t+1][1]);
             System.out.println("incProb[" + (t+1) + "][0] is " + incProb[0][emission1(increments.get(t+1))]);
             System.out.println("spreadProb[" + (t+1) + "][0] is " + spreadProb[0][emission2(spreads.get(t+1))]);
             System.out.println("incProb[" + (t+1) + "][1] is " + incProb[1][emission1(increments.get(t+1))]);
             System.out.println("spreadProb[" + (t+1) + "][1] is " + spreadProb[1][emission2(spreads.get(t+1))]);
             System.out.println("TRANS[1][0] " + trans[1][0]);
             System.out.println("TRANS[1][1] " + trans[1][1]);
             System.out.println("---------------------------------");


             System.out.println("KSI[" + t + "][1][0] is " + ksi[t][1][0]);
             System.out.println("KSI[" + t + "][1][1] is " + ksi[t][1][1]);

             if(t%100 == 0)
             new java.util.Scanner(System.in).nextLine();
             /***************************************************************/

            // Scale variables
            ksi[t][0][0] /= sum;
            ksi[t][0][1] /= sum;
            ksi[t][1][0] /= sum;
            ksi[t][1][1] /= sum;
        }
    }

    /*  Function to edit global variables gamma up to time n
    *
    */
    private void fillGamma(int n){
        for (int t = 0; t < n; ++t) {
            int sum = 0;
            for (int state = 0; state < 2; ++state) {
                gamma[t][state] = alpha[t][state]*beta[t][state];
                sum += gamma[t][state];
            }
            gamma[t][0] /= sum;
            gamma[t][1] /= sum;
        }

    }

    /*	Method to calculate errors and re-estimate HMM parameters
    *
    */
    private boolean calculateError(int n, double ACCURACY){

        System.out.println("gamma[0][0] is " + gamma[0][0]);
        System.out.println("gamma[0][1] is " + gamma[0][1]);

        // Re-estimate initial probabilities
        diff[0] = stateInitProb[0]-gamma[0][0];
        diff[1] = stateInitProb[1]-gamma[0][1];
        stateInitProb[0] = gamma[0][0];
        stateInitProb[1] = gamma[0][1];

        // Re-estimate transition probabilities
        int[] sum = new int[6];
        for (int t = 0; t < n-1; ++t) {
            for (int state1 = 0; state1 < 2; ++state1) {
                for (int state2 = 0; state2 < 2; ++state2) {
                    sum[state1*2 + state2] += ksi[t][state1][state2];
                }
                sum[state1+4] += gamma[t][state1];
            }
        }

        /****************************************
         *	TESTING PARAMS FOR ZERO
         */
        System.out.println("trans[0][0] is " + trans[0][0]);
        System.out.println("trans[0][1] is " + trans[0][1]);
        System.out.println("trans[1][0] is " + trans[1][0]);
        System.out.println("trans[1][1] is " + trans[1][1]);
        System.out.println("STATE 1 INIT PROB is " + stateInitProb[0]);	// NaN
        System.out.println("STATE 2 INIT PROB is " + stateInitProb[1]);	// NaN

        System.out.println("KSI VARIABLES");
        for (int i = 0; i<n; ++i) {
            System.out.println("KSI(1,1) is " + ksi[i][0][0]);	// ALL NaN
            System.out.println("KSI(1,2) is " + ksi[i][0][1]);
            System.out.println("KSI(2,1) is " + ksi[i][1][0]);
            System.out.println("KSI(2,2) is " + ksi[i][1][1]);
        }

        System.out.println("sum[4] is " + sum[4]);
        System.out.println("sum[4] is " + sum[4]);
        System.out.println("sum[4] is " + sum[4]);

			/*
        	****************************************/


        diff[2] 		= trans[0][0]-sum[0]/sum[4];

        diff[3] 		= trans[0][1]-sum[1]/sum[4];
        diff[4] 		= trans[1][0]-sum[2]/sum[5];
        diff[5] 		= trans[1][1]-sum[3]/sum[5];
        trans[0][0] 	= sum[0]/sum[4];
        trans[0][1] 	= sum[1]/sum[4];
        trans[1][0]		= sum[2]/sum[5];
        trans[1][1] 	= sum[3]/sum[5];
        System.out.println("CALCULATING ERROR");
        // Re-estimate emission probabilities
        sum = new int[2];
        int[][] sum1 = new int[2][9];
        int[][] sum2 = new int[2][3];

        for (int t = 0; t < n-1; ++t) {
            for (int state = 0; state < 2; ++state) {
                sum[state] += gamma[t][state];
                sum1[state][emission1(increments.get(t))] += gamma[t][state];
                sum2[state][emission2(spreads.get(t))] += gamma[t][state];
            }
        }

        for (int state =0; state < 2; ++state) {
            for(int j = 0; j < 9; ++j){
                diff[6+state*2+j] = incProb[state][j]-sum1[state][j]/sum[state];
                incProb[state][j] = sum1[state][j]/sum[state];
            }
        }

        for (int state =0; state < 2; ++state) {
            for(int j = 0; j < 3; ++j){
                diff[25+state*2+j] = spreadProb[state][j]-sum2[state][j]/sum[state];
                spreadProb[state][j] = sum1[state][j]/sum[state];
            }
        }

        boolean flag = true;
        for (int i=0; i<diff.length; ++i) {
            flag = flag && (diff[i]*diff[i] < ACCURACY);
        }

        return flag;
    }


    /*	Calibrates the HMM parameters via the Baum-Welch algorithm.
    *	@param: ACCURACY is the tolerance for error in the changes of the estimation
    *			MAX_ITER is the maximum number of iterations of the estimation
    *			n is the calibration window; calibrate over the first n data points
    */
    private void calibrate(int n, int MAX_ITER, double ACCURACY) {

        int ctr = 0;
        boolean stopFlag = false;
        while(!stopFlag){
            System.out.println("CALIBRATING: On iteration " + ctr);
            ctr++;
            fwdProbs(n);
            bwdProbs(n);
            fillKsi(n-1);
            fillGamma(n);
            stopFlag = calculateError(n,ACCURACY);
            stopFlag = (stopFlag || (ctr >= MAX_ITER));
        }
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
            ++totalTicks;
			log("I received a new bid of " + bid + ", and ask of " + ask);
            int spread = (int)Math.round(ask - bid);
            int midPrice = (int)Math.round(bid + spread/2.0);
            if (!midPrices.isEmpty()) {
                int increment = (int)Math.round(midPrice - midPrices.get(midPrices.size() - 1));
                log("Increment is: " + increment);
                log("Emission1 of the increment is: " + emission1(increment));
                last5Incs[4] = last5Incs[3];
                last5Incs[3] = last5Incs[2];
                last5Incs[2] = last5Incs[1];
                last5Incs[1] = last5Incs[0];
                last5Incs[0] = increment;
                increments.add(increment);
            }
            midPrices.add(midPrice);
            spreads.add((int)Math.round(ask - bid));
            int orderAmount = 0;
            double GSI = last5Incs[0] * 5 + last5Incs[1] * 4 + last5Incs[2] * 3 + last5Incs[3] * 2 + last5Incs[4];
            if (totalTicks > 5) {
                if (!positionOpen) {
                    if (GSI > 100) {
                        orderAmount = Math.max(MIN_POSITION - position, -5);
                        positionOpen = true;
                    }
                    else if (GSI > 75) {
                        orderAmount = Math.max(MIN_POSITION - position, -2);
                        positionOpen = true;
                    }
                    else if (GSI < -100) {
                        orderAmount = Math.min(MAX_POSITION - position, 5);
                        positionOpen = true;
                    }
                    else if (GSI < -75) {
                        orderAmount = Math.min(MAX_POSITION - position, 2);
                        positionOpen = true;
                    }
                }
                else {
                  if (lastGSI > 75) {
                      orderAmount = Math.min(5, -position);
                      positionOpen = false;
                  }
                  else if (lastGSI < -75) {
                      orderAmount = Math.max(-5, -position);
                      positionOpen = false;
                  }
                }
            }
            lastGSI = GSI;
            // Receive new bid and ask
            // Input your HMM algorithm here to calculate how much to buy or sell
            return orderAmount;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
			
            // Keep track of your own positions for your own benefit
            // Your position should not exceed 5 net
            position += volume;
            PnL -= volume * fillPrice;
            log("My current position is: " + position);
            log("Cost is: " + PnL);
		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}
