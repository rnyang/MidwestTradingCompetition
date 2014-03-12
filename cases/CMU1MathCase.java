import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import math.hmm.HMM;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.MatrixUtils;

/* Packages used for tests */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class CMU1MathCase extends AbstractMathCase implements MathCase {

    /* State between rounds */
    private IDB myDatabase;

    /* Constants */
    private static final RealVector spread1Signals = 
        MatrixUtils.createRealVector(
                new double[] {-4, -6, -8, 0, -1, -3, 0, 0, -1, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 3, 4, 6, 8});
    private static final RealVector spread2Signals = 
        MatrixUtils.createRealVector(
                new double[] {-2, -4, -6, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 4, 6});
    private static final RealVector spread3Signals = 
        MatrixUtils.createRealVector(
                new double[] {0, -2, -4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4});
    private static final RealVector[] spreadSignals = 
    {spread1Signals, spread2Signals, spread3Signals};

    /* Registered variables */
    private double buyThreshold;
    private double sellThreshold;
    private int buyUnit;
    private int trainPeriod;
    private int stopLossPeriod;
    private double stopLossLimit;

    /* Vars that keep track of state during round */
    private boolean stopLoss;
    private Queue<Double> prevPnLs;
    private int prevPrice;
    private HMM hmm;
    private List<Integer> priceChange;
    private List<Integer> spread;
    private int portfolio;
    private double cashProfit;

    /* API functions */

    /*
    //TODO comment out when using onRamp
    public MathCaseImpl() {
        this.hmm = new HMM();
        this.prevPrice = -1; 
        this.priceChange = new ArrayList<Integer>();
        this.spread = new ArrayList<Integer>();
        this.portfolio = 0;
        this.cashProfit = 0.0;
        this.prevPnLs = new LinkedList<Double>();
        this.stopLoss = false;

        this.buyThreshold = 0.2;
        this.sellThreshold = -0.2;
        this.buyUnit = 5;
        this.trainPeriod = 150;
        this.stopLossPeriod = 10;
        this.stopLossLimit = 250.0;
    }
    */

    public void addVariables(IJobSetup setup) {
        // Registers a variable with the system.
        setup.addVariable("buyThreshold", "ev threshold", "double", "0.2");
        setup.addVariable("sellThreshold", "ev threshold", "double", "-0.2");
        setup.addVariable("buyUnit", "amount to buy/sell at signal", "int",
        "5");
        setup.addVariable("trainPeriod", 
            "num of time steps before we start trading", "int", "150");
        setup.addVariable("stopLossPeriod", 
                          "period of time to consider loss over", 
                          "int", "10");
        setup.addVariable("stopLossLimit", "stop loss limit", "double", 
                          "250.0");
    }

    public void initializeAlgo(IDB database) {
        // Databases can be used to store data between rounds
        //myDatabase = database;

        this.hmm = new HMM();
        this.prevPrice = -1; 
        this.priceChange = new ArrayList<Integer>();
        this.spread = new ArrayList<Integer>();
        this.portfolio = 0;
        this.cashProfit = 0.0;
        this.prevPnLs = new LinkedList<Double>();
        this.stopLoss = false;

        this.buyThreshold = getDoubleVar("buyThreshold"); 
        this.sellThreshold = getDoubleVar("sellThreshold"); 
        this.buyUnit = getIntVar("buyUnit"); 
        this.trainPeriod = getIntVar("trainPeriod"); 
        this.stopLossPeriod = getIntVar("stopLossPeriod"); 
        this.stopLossLimit = getDoubleVar("stopLossLimit"); 
    }

    public int newBidAsk(double dBid, double dAsk) {
        if(this.stopLoss == true) return 0;
        if(this.prevPnLs.size() == this.stopLossPeriod) {
            double prevPnL = this.prevPnLs.peek();
            if(prevPnL - getPnL() >= this.stopLossLimit) {
                //System.out.println("Stop loss!");
                this.stopLoss = true;
                return -this.portfolio;
            }
            this.prevPnLs.remove();
        }
        this.prevPnLs.add(getPnL());

        int bid = (int) dBid;
        int ask = (int) dAsk;
        int spread = (ask - bid) / 2;
        int price = ask - spread;
        int prev = this.prevPrice;
        this.prevPrice = price;

        if(prev<0) return 0;

        int priceChange = price - prev;
        this.priceChange.add(priceChange);
        this.spread.add(spread);
        
        if (this.spread.size() <= this.trainPeriod) return 0;

        this.hmm.learn(this.priceChange, this.spread);

        /* Get matricies of interest from learnt hmm */
        int t = this.priceChange.size();
        RealMatrix A = MatrixUtils.createRealMatrix(hmm.getA());
        RealMatrix B = MatrixUtils.createRealMatrix(hmm.getB());
        RealVector pi_0 = MatrixUtils.createRealVector(hmm.getPi());

        /* Get current state */
        RealVector pi_t = currentState(pi_0, A, t);

        /* Computing expected value of different spreads */
        RealVector weightedB = B.preMultiply(pi_t);
        double ev = weightedB.dotProduct(spreadSignals[spread/2]);

        log("ev: "+ev);
        log("portfolio: "+this.portfolio);
        log("PnL: "+getPnL());
        if(ev > this.buyThreshold && this.portfolio <= 0) {
            return this.buyUnit;

        } else if(ev < this.sellThreshold && this.portfolio >= 0) {
            return -this.buyUnit;
        }

        return 0;
    }

    // For your own benefit, keep track of your own PnL too
    public void orderFilled(int volume, double fillPrice) {
        this.portfolio += volume;
        this.cashProfit -= volume * fillPrice;
    }


    public MathCase getMathCaseImplementation() {
        return this;
    }

    /* Helper functions */

    private double getPnL() {
        if (this.portfolio == 0 || this.spread.size() == 0)
            return this.cashProfit;
        int prevSpread = this.spread.get(this.spread.size()-1);

        if (this.portfolio > 0) {
            int prevBid = this.prevPrice - prevSpread;
            double marketValue = this.portfolio * prevBid;

            return this.cashProfit + marketValue;
        }

        int prevAsk = this.prevPrice + prevSpread;
        double marketValue = this.portfolio * prevAsk;

        return this.cashProfit + marketValue;
    }

    private static RealVector currentState(RealVector pi, RealMatrix A, 
                                          int t) {
        return A.power(t).preMultiply(pi);
    }

    /* Test input */
    public static void main(String[] args) {
        CMU1MathCase test = new CMU1MathCase();
        try{
            BufferedReader br = 
                new BufferedReader(new InputStreamReader(System.in));

            String input;

            br.readLine();
            while((input=br.readLine())!=null){
                String[] tokens = input.split(" ");
                double bid = Double.parseDouble(tokens[1]);
                double ask = Double.parseDouble(tokens[2]);
                int action = test.newBidAsk(bid, ask);
                if(action != 0) {
                    double fillPrice = action > 0 ? ask : bid;
                    test.orderFilled(action, fillPrice);
                }
                
            }

        }catch(IOException io){
            io.printStackTrace();
        }   

        //System.out.println("PnL: " + test.getPnL());
    }

}
