
import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


public class CHI2MathCase extends AbstractMathCase implements MathCase {


    private IDB myDatabase;
    int contract = 0;
    int numObservations = 1500;
    int lowerbound;
    int aggressiveFactor;
    double profit = 0;

    // our variables

    // initial state probabilities (given)
    int pi[] = {1, 0};

    // emission 1 probabilities
    // should be 2 x 9
    double b1[][];

    // emission 2 probabilities
    // should be 2 x 3
    double b2[][];

    // transition probabilities
    // should be 2 x 2
    double a[][];

    int[] marketPrices;
    int[] observations1;
    int[] observations2;
    int bids = 0;

    int counter1 = 0;
    public void addVariables(IJobSetup setup) {
        // Registers a variable with the system.
        setup.addVariable("lowerbound", "termination", "int", "-500");
        setup.addVariable("aggressiveFactor", "any value fewer than 5", "int", "1");
    }

    public void initializeAlgo(IDB database) {

        // Databases can be used to store data between rounds
        myDatabase = database;

        // helper method for accessing declared variables
        lowerbound = getIntVar("lowerbound");
        aggressiveFactor = getIntVar("aggressiveFactor");

        initializeProbabilities();
        log("probabilities initialized");
        log("initial matrix A: " + printMatrix(a, 2, 2));
    }

    public double Prediction2(int state_t)
    {

        log("state t: " + state_t);
        double sum = a[state_t][0] + a[state_t][1];
        // log("assurance of sum of transitions: " + sum);

        double changeMarketPrice = 0;
        // predict change in market price
        for(int i = 0; i < 9; i++)
        {
            // p(particular emission) x value of that emission x p(state 1) in time t+1
            changeMarketPrice += b1[0][i] * indextoPrice(i) * a[state_t][0];
            changeMarketPrice += b1[1][i] * indextoPrice(i) * a[state_t][1];
        }

        double newSpread = 0;
        // predict new spread
        for(int i = 0; i < 3; i++)
        {
            newSpread += b2[0][i] * indextoSpread(i) * a[state_t][0];
            newSpread += b2[1][i] * indextoSpread(i) * a[state_t][1];
        }

        log("changeMarketPrice: " +changeMarketPrice + "newSpread: " + newSpread);

        return changeMarketPrice;

    }
    // code of prediction function will be simplified after its
    // accuracy is known. it is simpler for debugging when variables are separated

    public double Prediction(int t, int T2)
    {
       // log("beginning prediction process");
        double[][] fwd2 = forwardProcedure(observations2, 2, T2);
        double[][] bwd2 = backwardProcedure(observations2, 2, T2);


       // log("done with forward/backward procedures");

        // calculate pi0,
        // the probability that at time t, it is in state 1 versus state 2
        /*
        double pState1t = gamma(0, t, fwd2, bwd2);
        double pState2t = gamma(1, t, fwd2, bwd2);
        */

        // use values from fwd/bwd procedures
        double pState1t = fwd2[0][T2-1];
        double pState2t = fwd2[1][T2-1];

        log("pi0, time t: " + pState1t + ", " + pState2t);

        // calculate pi1,
        // probabilities that given what state it is at time t,
        // at time t+1 it is in state 1 versus state 2
        double pState1t1 = (pState1t * a[0][0]) + (pState2t * a[0][1]);
        double pState2t1 = (pState1t * a[1][0]) + (pState2t * a[1][1]);

        double sum = pState1t1 + pState2t1;
        log("pi1, time t+1: " + pState1t1 + ", " + pState2t1 + "sum: " + sum);

        // calculate pi2,
        // probabilities that given state at t+1,
        // probabilities of various emissions
        double[] piEmission1 = new double[9];
        for (int i =0; i < 9; i++)
            piEmission1[i] = (b1[0][i] * pState1t1) + (b1[1][i] * pState2t1);

        // calculate pi3
        // probabilities that given state at t+1,
        // probabilities of various emissions
        double[] piEmission2 = new double[3];
        for (int i = 0; i < 3; i++)
            piEmission2[i] = (b2[0][i] * pState1t1) + (b2[1][i] * pState2t1);

        double expectedChangeMarketPrice = 0;
        // calculate expected ∆ market price
        for (int i = 0; i < 9; i++)
            expectedChangeMarketPrice += piEmission1[i] * indextoPrice(i);

        double expectedSpread = 0;
        // calculate expected spread
        for (int i = 0; i< 3; i++)
            expectedSpread += piEmission2[i] * indextoSpread(i);

        String prices = printArray(piEmission1);
        log("price emission probs: " + prices);
        log("expected change market price: " + expectedChangeMarketPrice);
        log("expected spread: " + expectedSpread);
        // log("previous spread: " + indextoSpread(observations2[t]));

        // return ∆marketPrice - newSpread - oldSpread
        double change = expectedChangeMarketPrice - expectedSpread - indextoSpread(observations2[t]);
        return change;


    }

    public String printArray(double[] array)
    {
        String value;
        String list = "";

        for (int i = 0; i < array.length; i++)
        {
            value = String.format("%.3f", array[i]);
            list = list + value + ", ";
        }

        return list;
    }

    public String printIntArray(int[] array)
    {
        String value;
        String list = "";

        for (int i = 0; i < array.length; i++)
        {
            value = String.format("%d", array[i]);
            list = list + value + ", ";
        }

        return list;
    }


    // train our estimations
    // given observed sequence observations, and number of iterations iter
    // public void train(int[] priceO, int[] spreadO, int iter, int T1, int T2)
    public void train(int[] priceO, int[]spreadO, int iter, int T1, int T2)
    {
        //int T1 = priceO.length;
        //int T2 = spreadO.length;
        double[][] forwardMatrix1;
        double[][] forwardMatrix2;
        double[][] backwardMatrix1;
        double[][] backwardMatrix2;

        double A[][] = new double[2][2];
        double B1[][] = new double[2][9];
        double B2[][] = new double[2][3];

        // log("beginning training");

        for (int r = 0; r < iter; r++)
        {
            // run the forward and backward procedures
            forwardMatrix1 = forwardProcedure(priceO, 1, T1);
            forwardMatrix2 = forwardProcedure(spreadO, 2, T2);
            backwardMatrix1 = backwardProcedure(priceO, 1, T1);
            backwardMatrix2 = backwardProcedure(spreadO, 2, T2);



            // update state transition probabilities
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    double top1 = 0;
                    double bottom1 = 0;
                    double top2 = 0;
                    double bottom2 = 0;
                    for (int t = 0; t <= T2 - 1; t++) {
                        top1 += xi(t, i, j, priceO, forwardMatrix1, backwardMatrix1, 1, T1);
                        bottom1 += gamma(i, t, forwardMatrix1, backwardMatrix1);
                        //top2 += xi(t, i, j, spreadO, forwardMatrix2, backwardMatrix2, 2, T2);
                        //bottom2 += gamma(i, t, forwardMatrix2, backwardMatrix2);
                    }

                    // which one should I choose to update state transition probabilities with?
                    // I can use an average or choose b2 since it has fewer elements
                    A[i][j] = divide(top1, bottom1);
                }
            }

            // update emission probabilities
            for (int i = 0; i < 2; i++)
            {
                for (int k = 0; k < 9; k++)
                {
                    double top = 0;
                    double bottom = 0;

                    for (int t = 0; t<= T1 - 1; t++)
                    {
                        double g = gamma(i, t, forwardMatrix1, backwardMatrix1);
                        top += g * (k == priceO[t] ? 1 : 0);
                        bottom += g;
                    }

                    B1[i][k] = divide(top, bottom);
                }

                for (int h = 0; h < 3; h++)
                {
                    double top2 = 0;
                    double bottom2 = 0;

                    for (int t = 0; t<= T2 - 1; t++)
                    {
                        double g2 = gamma(i, t, forwardMatrix2, backwardMatrix2);
                        top2 += g2 * (h == spreadO[t] ? 1 : 0);
                        bottom2 += g2;
                    }

                    B2[i][h] = divide(top2, bottom2);
                }
            }

            a = A;
            b1 = B1;
            b2 = B2;
        }

        // log("done with training");
        log("matrix a: " + printMatrix(a, 2, 2));
        log("matrix b1: " + printMatrix(b1, 2, 9));
        log("matrix b2: " + printMatrix(b2, 2, 3));

    }

    public String printMatrix(double[][] matrix, int rows, int columns)
    {
        String list = "[";
        String value = "";
        int counter = 0;

        for (int i = 0; i < rows; i++)
        {
            for (int j = 0; j < columns; j++)
            {
                // value = Double.toString(matrix[i][j]);
                value = String.format("%.3f", matrix[i][j]);
                list = list + value + ", ";
                counter++;
            }
            list = list + "\n";

        }
        list = list + "]";

        return list;

    }

    // calculate the forward procedure matrix for state i at time t for
    // observation sequence "observations", emission probabilities matrix selected by int B
    // [1 means b1, 2 means b2]
    // returned matrix is over i, states, and t, times
    public double[][] forwardProcedure(int[] observations, int B, int T){
        // int T = observations.length;

        double[][] forwardMatrix = new double[2][T];

        // base case is time = 0
        // 2 is the number of states (define variables in Java?)
        for (int i = 0; i < 2; i++)
        {
            if (B == 1)
                forwardMatrix[i][0] = pi[i] * b1[i][observations[0]];
            else if (B == 2)
                forwardMatrix[i][0] = pi[i] * b2[i][observations[0]];
            else
                log("ERROR: invalid B matrix value in initialization FP");
        }

        // why T-2 instead of T-1?
        // iterate over observations
        for (int t = 0; t <= T-2; t++)
        {
            // iterate over states
            for (int j = 0; j < 2; j++)
            {
                forwardMatrix[j][t+1] = 0;
                // summation part of the forward procedure algorithm
                for (int i = 0; i < 2; i++)
                    forwardMatrix[j][t+1] += (forwardMatrix[i][t] * a[i][j]);
                // then multiply value by B(ot+1)
                if (B == 1)
                    forwardMatrix[j][t+1] *= b1[j][observations[t+1]];
                else if (B == 2)
                    forwardMatrix[j][t+1] *= b2[j][observations[t+1]];
                else
                    log("ERROR: invalid B matrix value in FP" + B);
            }
        }

        // String fwdPrint = printMatrix(forwardMatrix, 2, T);
        //log("forwardMatrix: " + fwdPrint);
        return forwardMatrix;
    }

    // calculate the backward procedure matrix for state i at time t for
    // observation sequence "observations", emission probabilities matrix selected by int B
    // [1 means b1, 2 means b2]
    // returned matrix is over states i and times t
    public double[][] backwardProcedure(int[] observations, int B, int T){
        // int T = observations.length;
        double[][] backwardMatrix = new double[2][T];

        // base case is time T-1
        // 2 is the number of states
        for (int i = 0; i < 2; i++)
            backwardMatrix[i][T-1] = 1;

        // iterate over observations, decreasing
        for (int t = T-2; t >= 0; t--)
        {
            // iterate over states
            for (int i = 0; i < 2; i++)
            {
                // empty out value
                backwardMatrix[i][t] = 0;
                // summation part of backwards procedure algo:
                // iterate over states
                for (int j = 0; j < 2; j++)
                {
                    if (B == 1)
                        backwardMatrix[i][t] += (backwardMatrix[j][t+1] * a[i][j] * b1[j][observations[t+1]]);
                    else if (B == 2)
                        backwardMatrix[i][t] += (backwardMatrix[j][t+1] * a[i][j] * b2[j][observations[t+1]]);
                    else
                        log("ERROR: invalid B matrix value in BP: " + B);
                }
            }
        }
        return backwardMatrix;
    }

    // calculate ξ(t) == p(state_t=i, state_t+1 =j|O,λ)
    // given time t, state_t i, state_t+1 j, observed sequence observations,
    //  forward variables forwardMatrix, backward variables backwardMatrix, emission B (either 1 or 2)
    // return probability

    public double xi(int t, int i, int j, int[] observations, double[][] forwardMatrix, double[][] backwardMatrix, int B, int T)
    {
        double top, bottom, xi;
        bottom = 0;

        if (t == T - 1)
            top = forwardMatrix[i][t] * a[i][j];

        else
        {
            if (B == 1)
                top = forwardMatrix[i][t] * a[i][j] * b1[j][observations[t+1]] *  backwardMatrix[j][t+1];
            else
                top = forwardMatrix[i][t] * a[i][j] * b2[j][observations[t+1]] *  backwardMatrix[j][t+1];
        }

        for (int k = 0; k < 2; k++)
            bottom += (forwardMatrix[k][t] * backwardMatrix[k][t]);

        xi = divide(top, bottom);

        return xi;
    }

    // calculate γ(t) == p(state_t = i | O,λ)
    // given time t, state_t i, observed sequence observations,
    //  forward variables forwardMatrix, backward variables backwardMatrix,
    // return probability
    public double gamma(int i, int t, double[][] forwardMatrix, double[][] backwardMatrix)
    {
        double top, bottom, gamma;

        top = forwardMatrix[i][t] * backwardMatrix[i][t];
        bottom = 0;

        for (int j = 0; j < 2; j++)
            bottom += forwardMatrix[j][t] * backwardMatrix[j][t];

        gamma = divide(top, bottom);

        return gamma;
    }

    // account for possible division errors with denominator 0
    public double divide(double a, double b)
    {
        if (a == 0)
            return 0;

        else
            return a/b;
    }


    public void storeObservations(double bid, double ask)
    {
        int askCasted = (int) ask;
        int bidCasted = (int) bid;

        int spread = (askCasted - bidCasted) / 2;

        // keep a log of the market prices
        marketPrices[counter1] = askCasted - spread;
        log("marketPrice being logged: " + marketPrices[counter1]);

        // keep track of emissions
        if (counter1 > 0)
        {
            // emission 2 is spread from {1, 3, 5}
            observations2[counter1 - 1] = spreadtoIndex(spread);
            // emission 1 is change in market price, selected from {0, ±1, ±3, ±5, ±10}
            observations1[counter1 - 1] = pricetoIndex(marketPrices[counter1] - marketPrices[counter1 - 1]);
            int difference = marketPrices[counter1] - marketPrices[counter1 - 1];
            log("change in marketPrice being logged: " + marketPrices[counter1] + " - " + marketPrices[counter1-1] + " = " + difference);
        }
    }

    // translate market price value into matrix index
    public int pricetoIndex(int price)
    {
        switch(price)
        {
            case -10:
                return 0;
            case -5:
                return 1;
            case -3:
                return 2;
            case -1:
                return 3;
            case 0:
                return 4;
            case 1:
                return 5;
            case 3:
                return 6;
            case 5:
                return 7;
            case 10:
                return 8;
            default:
                log("ERROR in pricetoIndex: " + price);

        }
        return 0;
    }

    // translate matrix index into market price value
    public int indextoPrice(int index)
    {
        switch(index)
        {
            case 0:
                return -10;
            case 1:
                return -5;
            case 2:
                return -3;
            case 3:
                return -1;
            case 4:
                return 0;
            case 5:
                return 1;
            case 6:
                return 3;
            case 7:
                return 5;
            case 8:
                return 10;
            default:
                log("ERROR in indextoPrice");

        }
        return 0;
    }

    // translate spread value into matrix index
    public int spreadtoIndex(int spread)
    {
        switch(spread)
        {
            case 1:
                return 0;
            case 3:
                return 1;
            case 5:
                return 2;
            default:
                log("ERROR in spreadtoIndex");
        }

        return 0;
    }

    // translate matrix index into spread value
    public int indextoSpread(int index)
    {
        switch(index)
        {
            case 0:
                return 1;
            case 1:
                return 3;
            case 2:
                return 5;
            default:
                log("ERROR in indextoSpread");
        }

        return 0;
    }

    public void initializeProbabilities()
    {
        // emission 1 probabilities
        // should be 2 x 9
        double B1[][] = new double[2][9];

        for(int i = 0; i < 2; i++)
        {
            for(int j = 0; j < 9; j++)
                B1[i][j] = .5;
        }

        // emission 2 probabilities
        // should be 2 x 3
        double B2[][] = new double[2][3];

        for(int i = 0; i < 2; i++)
        {
            for(int j = 0; j < 3; j++)
                B2[i][j] = .5;
        }

        // transition probabilities
        // should be 2 x 2
        double A[][] = new double[2][2];

        for(int i = 0; i < 2; i++)
        {
            for(int j = 0; j < 2; j++)
                A[i][j] = .5;
        }

        int[]MarketPrices = new int[numObservations];
        int[]Observations1 = new int[numObservations];
        int[]Observations2 = new int[numObservations];

        marketPrices = MarketPrices;
        observations1 = Observations1;
        observations2 = Observations2;


        b1= B1;
        b2 = B2;
        a = A;


    }

    public int[] viterbi(int[] SP, int []Y, double[][] TP, double[][] EP, int t)
    {
        // number of states
        int k = 2;
        // number of emission types
        int n = 3;
        double[][] t1 = new double[k][t];
        int[][] t2 = new int[k][t];
        int[] x = new int[t];
        for (int i = 0; i < k; i++)
        {
            t1[i][0] = SP[i] * EP[i][Y[0]];
            t2[i][0] = 0;
        }
        for (int i = 1; i < t; i++)
        {
            for (int j = 0; j < k; j++)
            {
                t2[j][i] = 0;
                t1[j][i] = t1[0][i - 1] * TP[0][j] * EP[j][Y[i]];
                for (int l = 1; l < k; l++)
                {
                    if (t1[l][i - 1] * TP[l][j] * EP[j][Y[i]] > t1[j][i])
                    {
                        t2[j][i] = l;
                        t1[j][i] = t1[l][i - 1] * TP[l][j] * EP[j][Y[i]];
                    }
                }
            }
        }
        for (int i = t - 1; i >= 0; i--)
        {
            x[i] = 0;
            for (int j = 1; j < k; j++)
            {
                if (t1[x[i]][i] < t1[j][i])
                {
                    x[i] = j;
                }
            }
        }
        return x;
    }


    public int newBidAsk(double bid, double ask) {
        log("I received a new bid of " + bid + ", and ask of " + ask);
        log("number of bids: " + bids);
        bids++;

        storeObservations(bid, ask);
        counter1++;

        if (counter1 == 100 || counter1 == 200 || counter1 == 300)
        {
            train(observations1, observations2, 80, counter1-2, counter1-2);
        }

        if (counter1 > 300)
        {
            // train(observations1, observations2, 100, counter1-2, counter1-2);
            //double predictedChange = Prediction(counter1-2, counter1-1);
            // train(observations1, observations2, 40, counter1-2, counter1-2);

            double state1change = Prediction2(0);
            double state2change = Prediction2(1);
            int [] vit = viterbi(pi, observations2, a, b2, counter1-1);
            int state_t = vit[counter1-2];
            log("changes: " + state1change + state2change);
            log("state_t: " + state_t);


            if(profit < lowerbound && contract == 0)
                return 0;

            if(state_t == 0)
            {
                // this means I think price will go up in state 1
                if (state1change > state2change)
                {
                    if (contract <= (5-aggressiveFactor))
                        return aggressiveFactor;
                }
                if (state2change > state1change)
                    return -contract;
            }
            if (state_t == 1)
            {
                if (state2change > state1change)
                {
                    if (contract <= (5-aggressiveFactor))
                        return aggressiveFactor;
                }
                if (state1change > state2change)
                    return -contract;
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
        log("My current position is: " + contract);

        profit += -volume * fillPrice;
        log("Profit: " + profit);

    }


    public MathCase getMathCaseImplementation() {
        return this;
    }

}
