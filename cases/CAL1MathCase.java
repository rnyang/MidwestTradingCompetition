import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

// Ideas:
// more iterations
// longer training period
// multiplicative weights: learn over time so that you get out of bad trade
// correlated distr (with eps)
// change priors
// re-estimate params

public class CAL1MathCase extends AbstractMathCase implements MathCase {

    int money = 0;  // how much cash, not including stocks
    int position_index = 5;  // internal state (assuming all requests are filled, which they should be)
    double p0;  // prob state S0

    int time = 0;  // current time (might be wrong due to frame skips)
    int max_t = 1200;  // time when game ends
    int est_t = 500;  // time at which to estimate params
    int prev_price = -1;  // -1 when "unavailable", i.e. don't make a move this turn

    boolean done = false;  // if done trading

    // hidden Markov model variables
    // estimated values, used by algorithm
    int[] change_val = {-10, -5, -3, -1, 0, 1, 3, 5, 10};
    int[] spread_val = {1, 3, 5};
    double[][] p;  // state transition p[i,j] = prob i->j
    double[][][] distr; // = new double[2][change_val.length][spread_val.length];  // distr[i,c,s] = Pr[c and s | state S_i]
    boolean paramsEstimated = false;

    int[] observed_change = new int[est_t];
    int[] observed_spread = new int[est_t];

    // dynamic programming variables
    int num_timesteps = max_t - est_t + 1;  // number of DP timesteps
    int num_discrete = 15;  // discretization of [0,1]: {0, 1/(num_discrete-1), 2/(num_discrete-1), ..., 1}
    int[] positions = {-5,-4,-3,-2,-1,0,1,2,3,4,5};

    // DP[t, x, s, d] = expected return; t = timestep, x = position index, s = spread index, d = discrete index
    // change and spread have just been observed
    // x is current position
    // current price is 0 (after change)
    // prob S0 (discrete d) takes into account info provided by change, spread just observed
    double[][][][] DP = new double[num_timesteps][positions.length][spread_val.length][num_discrete];  // expected return
    double[][][][] DP_move = new double[num_timesteps][positions.length][spread_val.length][num_discrete];  // best new_x

    public static void main(String[] args) {

        CAL1MathCase mc = new CAL1MathCase();

        // "true" params used for simulation

        /*
        // easy
        double[][] p = {{0.7, 0.3}, {0.2, 0.8}};
        double[][][] distr = new double[2][mc.change_val.length][mc.spread_val.length];
        distr[0][2][2] = 1;
        distr[1][8][0] = 0.5;
        distr[1][5][1] = 0.5;
        */

        // spread index always 1
        // 0 - slow increase
        // 1 - sharp decrease
        // performance: doesn't do too well at estimating p, still makes some money
        // only makes money by buying and waiting; it goes up on average

        // hard: on average market goes neither up nor down
        // spread index always 1
        // performance: bad, usually guesses all-in or all-out (or zero)
        // occassionally makes money using non-trivial strategy
        // tried with both 3 and 9 bins
        double[][] p = {{0.9, 0.1}, {0.1, 0.9}};
        double[][][] distr = new double[2][mc.change_val.length][mc.spread_val.length];
        distr[0][0][1] = 5;
        distr[0][1][1] = 9;
        distr[0][2][1] = 7;
        distr[0][3][1] = 4;
        distr[0][4][1] = 10;
        distr[0][5][1] = 13;
        distr[0][6][1] = 18;
        distr[0][7][1] = 21;
        distr[0][8][1] = 15;
        distr[1][0][1] = 15;
        distr[1][1][1] = 21;
        distr[1][2][1] = 18;
        distr[1][3][1] = 13;
        distr[1][4][1] = 10;
        distr[1][5][1] = 4;
        distr[1][6][1] = 7;
        distr[1][7][1] = 9;
        distr[1][8][1] = 5;
        normalizeArray3D(distr);

        int price = 10000;
        int state = (Math.random() < 0.5) ? 0 : 1;
        for (int t = 1 ; t <= mc.max_t ; t++) {
            state = (Math.random() < p[state][0]) ? 0 : 1;
            double rnd = Math.random();
            double sum = 0;
            boolean done = false;
            for (int i = 0 ; i < mc.change_val.length ; i++) {
                for (int j = 0 ; j < mc.spread_val.length ; j++) {
                    sum += distr[state][i][j];
                    if (sum > rnd) {

                        price += mc.change_val[i];
                        int spread = mc.spread_val[j];
                        mc.newBidAsk(price - spread, price + spread);

                        done = true;
                        break;
                    }
                }
                if (done) break;
            }
        }
    }

    public void addVariables(IJobSetup setup) {
        // Registers a variable with the system.
        setup.addVariable("stop", "Set to 'true' to stop trading", "boolean", "false");
        setup.addVariable("target", "Stop trading once you make this much profit (or -1 to never stop)", "int", "-1");
    }

    public void initializeAlgo(IDB database) {

        // Databases can be used to store data between rounds
        //myDatabase = database;

        // helper method for accessing declared variables
        //factor = getIntVar("someFactor");
    }

    public int newBidAsk(double bid, double ask) {

        try {
            boolean stop = getBooleanVar("stop");
            if (stop) {
                logM("stopped");
                done = true;
                return 0;
            }
        } catch(Exception e) {
        }

        if (done) {
            return 0;
        }

        time++;

        int spread = (int)(ask - bid) / 2;
        int price = (int)bid + spread;
        int change = price - prev_price;
        prev_price = price;

        logM("Time = " + time + "; Price = " + price + "; Spread = " + spread);

        // last timestep
        //if (time == max_t) {
        //logM("Ending position is: " + positions[position_index] + ". Auto-selling/buying.");
        int profit = money + positions[position_index] * price - Math.abs(positions[position_index]) * spread;
        //logM("position_index: " + position_index);
        //logM("position: " + positions[position_index]);
        logM("money: " + money);
        logM("profit: " + profit);
        // TODO: keep track of profit based on callback function
        //}

        // if already achieved target, stop
        int target = -1;
        try {
            target = getIntVar("target");
        }
        catch(Exception e){
        }
        if (profit >= target && target != -1) {
            logM("achieved target, done");
            int num_bought = - positions[position_index];
            position_index = 5;
            done = true;
            return num_bought;
        }

        // get change, spread indices
        int change_index = -1;
        int spread_index = -1;
        for (int i = 0 ; i < change_val.length; i++) {
            if (change == change_val[i])
                change_index = i;
        }
        for (int i = 0 ; i < spread_val.length; i++) {
            if (spread == spread_val[i])
                spread_index = i;
        }

        // if we skipped some frames, do nothing
        if (change_index == -1) {
            return 0;
        }

        if (time < est_t) {
            newDataPoint(change_index, spread_index);
            return 0;
        }
        if (time >= est_t && !paramsEstimated) {
            logM("prior p");
            printArray(a);
            logM("prior distr (bins)");
            printArray(b);

            estimateParams();
            logM("PARAMETERS ESTIMATED");

            logM("p");
            printArray(p);
            logM("distr (bins)");
            printArray(b);
            logM("distr0");
            printArray(distr[0]);
            logM("distr1");
            printArray(distr[1]);


            // TESTING: cheat and use real params
            /*
            double[][] temp = {{0.7, 0.3}, {0.2, 0.8}};
            p = temp;
            distr = new double[2][change_val.length][spread_val.length];
            distr[0][2][2] = 1;
            distr[1][8][0] = 0.5;
            distr[1][5][1] = 0.5;
            */


            logM("building DP...");
            buildDP();  // build DP using new parameters
            logM("done building DP");

            // if just estimated params, don't try to make a move right now (because might have skipped frames)
            prev_price = -1;
            return 0;
        }

        int move = getBestMove(change_index, spread_index);

        // keep track of how much money that move gives us; update our position (TODO: move to callback function)
        int num_bought = positions[move] - positions[position_index];
        position_index = move;
        //logM("num_bought: " + num_bought);
        //logM("money before: " + money);
        money -= (num_bought) * price + Math.abs(num_bought) * spread_val[spread_index];
        //logM("money after: " + money);

        return num_bought;
    }

    // For your own benefit, keep track of your own PnL too
    public void orderFilled(int volume, double fillPrice) {

        //logM("My order was filled with qty of " + volume + " at a price of " + fillPrice);

        // Keep track of your own positions for your own benefit
        // Your position should not exceed 5 net
        //contract = contract + volume;
        //logM("My current position is: " + contract);

    }

    // Save new data point for estimation later
    public void newDataPoint(int change_index, int spread_index) {
        observed_change[time-1] = change_index;
        observed_spread[time-1] = spread_index;
    }

    // Estimate parameters
    public void estimateParams() {

        // *** remember to change sigmaSize and b (prior) according to binning strategy

        // create binned observed vector for training
        // 3 states: down, none, up
        // change_index: 0-3 (down) 4-6 (none) 7-10 (up)
        ///*
        int[] o = new int[observed_change.length];
        for (int i = 0 ; i < o.length ; i++) {
            o[i] = (observed_change[i] <= 3) ? 0 : ((observed_change[i] <= 6) ? 1 : 2);
        }
        //*/

        // alternating binning: separate state for each change, ignore spread
        /*
        int[] o = new int[observed_change.length];
        for (int i = 0 ; i < o.length ; i++) {
            o[i] = observed_change[i];
        }
        */

        int num_iter = 1000;
        double[] gam = train(o, num_iter);

        // convert output to distribution
        p = a;
        normalizeArray(p);  // make sure rows sum to 1
        /*
        for (int s = 0 ; s < 2 ; s++) {
            for (int i = 0 ; i < change_val.length ; i++) {
                for (int j = 0 ; j < spread_val.length ; j++) {
                    distr[s][i][j] = b[s][i * spread_val.length + j];
                }
            }
        }
        */
        // convert "binned" distribution to full distribution
        // for now just do crude uniform approx
        // eventually should be better, use observed frequencies
        /*
        normalizeArray(b);
        for (int i = 0 ; i < 2 ; i++) {
            for (int c = 0 ; c < change_val.length ; c++) {
                int ind = (c <= 3) ? 0 : ((c <= 6) ? 1 : 2);
                int num = (c >= 4 && c <= 6) ? 3 : 4;  // how many numbers in this bin
                for (int s = 0 ; s < spread_val.length ; s++) {
                    distr[i][c][s] = b[i][ind] / (3 * num);  // divide by 3 for the 3 spread values
                }
            }
        }
        */

        // improved estimation of full distribution (distr)
        // use gamma and get frequencies
        // don't even use b (estimated binned emissions)
        // gam gives prob of being in S0 at each timestep
        // observed_change and observed_spread give observed values
        // result: distr[state][change][spread] = prob
        // need to make sure all entires of distr are positive or else NaN errors
        /*
        distr = new double[2][change_val.length][spread_val.length];
        double sum0 = 0, sum1 = 0;
        for (int i = 0 ; i < observed_change.length ; i++) {
            distr[0][observed_change[i]][observed_spread[i]] += gam[i];
            distr[1][observed_change[i]][observed_spread[i]] += 1-gam[i];
            sum0 += gam[i];
            sum1 += 1-gam[i];
        }
        // normalize
        for (int c = 0 ; c < change_val.length ; c++) {
            for (int s = 0 ; s < spread_val.length ; s++) {
                distr[0][c][s] /= sum0;
                distr[1][c][s] /= sum1;
            }
        }
        */

        // independent distribution performs badly on correlated true params
        // but correlated distribution gives 0 outcomes
        // could try correlated but force position (epsilon) entries

        // alternative: independent distribution (change and spread)
        // avoids certain outcomes having 0 probability (which gives NaN errors)
        ///*
        distr = new double[2][change_val.length][spread_val.length];
        double change_sum_0 = 0, change_sum_1 = 0, spread_sum_0 = 0, spread_sum_1 = 0;
        double[][] change_prob = new double[2][change_val.length];
        double[][] spread_prob = new double[2][spread_val.length];
        for (int i = 0 ; i < observed_change.length ; i++) {
            change_prob[0][observed_change[i]] += gam[i];
            change_prob[1][observed_change[i]] += 1-gam[i];
            change_sum_0 += gam[i];
            change_sum_1 += 1-gam[i];
            spread_prob[0][observed_spread[i]] += gam[i];
            spread_prob[1][observed_spread[i]] += 1-gam[i];
            spread_sum_0 += gam[i];
            spread_sum_1 += 1-gam[i];
        }
        for (int c = 0 ; c < change_val.length ; c++) {
            for (int s = 0 ; s < spread_val.length ; s++) {
                distr[0][c][s] = change_prob[0][c] * spread_prob[0][s] / (change_sum_0 * spread_sum_0);
                distr[1][c][s] = change_prob[1][c] * spread_prob[1][s] / (change_sum_1 * spread_sum_1);
            }
        }
        //*/

        // TESTING: sanity check on distr
        /*
        double sum0 = 0;
        double sum1 = 0;
        for (int c = 0 ; c < change_val.length ; c++) {
            for (int s = 0 ; s < spread_val.length ; s++) {
                sum0 += distr[0][c][s];
                sum1 += distr[1][c][s];
            }
        }
        logM("sanity check: " + sum0 + ", " + sum1);
        */

        // TESTING: compute expected change in each state based on distr
        double sum0 = 0;
        double sum1 = 0;
        for (int c = 0 ; c < change_val.length ; c++) {
            sum0 += change_val[c] * change_prob[0][c] / change_sum_0;
            sum1 += change_val[c] * change_prob[1][c] / change_sum_1;
        }
        logM("expected changes: " + sum0 + ", " + sum1);
        // also compute expected overall trajectory
        // need stationary state for markov chain
        double v0 = 1;
        double v1 = (1-p[0][0])/p[1][0];
        double sum = v0 + v1;
        v0 /= sum;
        v1 /= sum;
        double traj = v0 * sum0 + v1 * sum1;
        logM("trajectory: " + traj);


        // current prob of S0
        p0 = 0.5;  // TODO: update based on gamma but worry about frame skips

        paramsEstimated = true;
    }

    // Build DP array
    public void buildDP() {

        // fill out last line of DP
        // liquidate stock (assuming price = 0)
        // if x positive, sell stocks for price -<spread>
        // if x negative, need to buy stocks at price <spread>
        // either way, lose money
        int t = num_timesteps - 1;
        for (int x = 0 ; x < positions.length ; x++) {
            for (int s = 0 ; s < spread_val.length ; s++) {
                for (int d = 0 ; d < num_discrete ; d++) {
                    DP[t][x][s][d] = -Math.abs(positions[x]) * spread_val[s];
                }
            }
        }

        // dynamic programming step
        t--;
        while (t >= 0) {
            for (int x = 0 ; x < positions.length ; x++) {  // current position
                for (int s = 0 ; s < spread_val.length ; s++) {  // current spread
                    for (int d = 0 ; d < num_discrete ; d++) {  // current prob S0 (discrete) -- considering change and spread
                        double prob0 = d / ((double)(num_discrete-1));
                        double[] payoffs = new double[positions.length];  // payoff for each new position
                        for (int new_x = 0 ; new_x < positions.length ; new_x++) {  // new position index
                            double sum = 0;  // payoff for this x_new
                            // computing payoff for (t,x,s,d) with choice new_x
                            // scenario: new_s, change
                            // underlying: state, new_state
                            for (int new_s = 0 ; new_s < spread_val.length ; new_s++) {  // new spread index
                                for (int change = 0 ; change < change_val.length ; change++) {  // change index
                                    // scenario: new_s, change
                                    double prob = 0;  // prob that this scenario occurs
                                    double prob_term_0 = 0;  // prob[this scenario AND next state S0]
                                    for (int state = 0 ; state <= 1 ; state++) {  // current state S0 or S1
                                        double prob_state = (state == 0) ? prob0 : 1-prob0;  // prob that 'state' is current state
                                        for (int new_state = 0 ; new_state <= 1 ; new_state++) {  // new state S0 or S1
                                            // underlying: state, new_state
                                            // increase prob by prob of this underlying
                                            double prob_term = prob_state * p[state][new_state] * distr[new_state][change][new_s];  // prob of this scenario and this underlying
                                            prob += prob_term;
                                            if (new_state == 0) {
                                                prob_term_0 += prob_term;
                                            }
                                        }
                                    }

                                    // computations for this scenario
                                    if (prob > 0) {
                                        double new_p0 = prob_term_0 / prob;  // next belief for S0 in this scenario
                                        double new_d = new_p0 * (num_discrete-1);  // fractional d index
                                        // compute payoff for this scenario
                                        // profit from stock sold this turn: -Math.abs(x - new_x) * s
                                        // price correction: new_x * change
                                        // inductive value: DP[t+1, new_x, new_s, d] (weighted over d)
                                        double lambda = new_d - Math.floor(new_d);
                                        double weighted_val = lambda * DP[t+1][new_x][new_s][(int)Math.ceil(new_d)] + (1-lambda) * DP[t+1][new_x][new_s][(int)Math.floor(new_d)];
                                        double payoff = -Math.abs(positions[x] - positions[new_x])*spread_val[s] + positions[new_x]*change_val[change] + weighted_val;  // payoff of this scenario
                                        sum += prob * payoff;
                                    }
                                }
                            }
                            payoffs[new_x] = sum;
                        }
                        // choose which x_new has best payoff
                        int best_ind = 0;
                        double best = payoffs[0];
                        for (int i = 1 ; i < payoffs.length; i++) {
                            if (payoffs[i] > best) {
                                best = payoffs[i];
                                best_ind = i;
                            }
                        }
                        // now payoff for this DP cell is 'best'
                        // best choice for x_new is 'best_ind'
                        DP[t][x][s][d] = best;
                        DP_move[t][x][s][d] = best_ind;
                    }
                }
            }
            t--;
        }
    }

    // Get best move based on DP array
    // Output is new position you should take
    public int getBestMove(int change_index, int spread_index) {

        // code for deciding on a move
        // input: change, spread

        int t = time - est_t;  // time since params were estimated

        // TESTING: use "stationary distribution"
        //t = 0;

        // compute new prob of S0 based on observed change, spread
        // know p0 = prob previously in S0
        // know change_index, spread_index emission from current new state
        double prob_0 = p0 * p[0][0] * distr[0][change_index][spread_index] + (1-p0) * p[1][0] * distr[0][change_index][spread_index];  // prob[observe this outcome AND current state 0]
        double prob_1 = p0 * p[0][1] * distr[1][change_index][spread_index] + (1-p0) * p[1][1] * distr[1][change_index][spread_index];  // prob[observe this outcome AND current state 1]
        p0 = prob_0 / (prob_0 + prob_1);  // prob[current state 0 | observed]

        logM("p0: " + p0);

        double d = p0 * (num_discrete-1);  // discretize current prob of S0
        double lambda = d - Math.floor(d);
        double weighted_move = lambda * DP_move[t][position_index][spread_index][(int)Math.ceil(d)] + (1-lambda) * DP_move[t][position_index][spread_index][(int)Math.floor(d)];
        int move = (Math.random() < weighted_move-Math.floor(weighted_move)) ? (int)Math.ceil(weighted_move) : (int)Math.floor(weighted_move);  // random sampling

        logM("Move: " + positions[move]);

        // print expected payoff
        double weighted_payoff = lambda * DP[t][position_index][spread_index][(int)Math.ceil(d)] + (1-lambda) * DP[t][position_index][spread_index][(int)Math.floor(d)];
        logM("Expected payoff: " + weighted_payoff);

        return move;
    }


    public MathCase getMathCaseImplementation() {
        return this;
    }






    /******************************/
    /** HIDDEN MARKOV MODEL CODE **/
    /******************************/





    /** number of states */
    public int numStates = 2;

    /** size of output vocabulary */
    public int sigmaSize = 3;//9  // 3 states (down, none, up)

    /** initial state probabilities */
    public double pi[] = {0.5, 0.5};

    /** transition probabilities */
    public double a[][] = {{0.5, 0.5}, {0.5, 0.5}};//{{0.8, 0.2}, {0.2, 0.8}};  // prior (TODO)

    /** emission probabilities */
    public double b[][] = {{0.65, 0.3, 0.05}, {0.05, 0.3, 0.65}};  //{{0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.2,0.3},{0.3,0.2,0.1,0.1,0.1,0.05,0.05,0.05,0.05}};  // prior

    public static double log0 = -999;

    /** implementation of the Baum-Welch Algorithm for HMMs.
     @param o the training set
     @param steps the number of steps
     */
    public double[] train(int[] o, int steps) {

        // output = gamma; gamma[t] = prob[in state 0 at time t]

        /*
        // print o
        String str = "";
        for (int i = 0 ; i < o.length ; i++) {
            str += o[i] + " ";
        }
        logM(str);
        */

        //logM("a");
        //printArray(a);
        //logM("b");
        //printArray(b);
        //logM("pi: " + pi[0] + " " + pi[1]);

        int T = o.length;
        double[][] fwd = {{}};
        double[][] bwd = {{}};

        double pi1[] = new double[numStates];
        double a1[][] = new double[numStates][numStates];
        double b1[][] = new double[numStates][sigmaSize];

        for (int s = 0; s < steps; s++) {
             /* calculation of Forward- und Backward Variables from the current model */
            fwd = forwardProc(o); //log form
            bwd = backwardProc(o); // log form

            /* re-estimation of initial state probabilities */
            for (int i = 0; i < numStates; i++)
                pi1[i] = gamma(i, 0, o, fwd, bwd); // NOT log form

            /* re-estimation of transition probabilities */
            for (int i = 0; i < numStates; i++) {
                for (int j = 0; j < numStates; j++) {
                    double num = 0;
                    double denom = 0;
                    for (int t = 0; t <= T - 1; t++) {
                        num += p(t, i, j, o, fwd, bwd);
                        denom += gamma(i, t, o, fwd, bwd);
                    }
                    a1[i][j] = divide(num, denom);
                }
            }

            /* re-estimation of emission probabilities */
            for (int i = 0; i < numStates; i++) {
                for (int k = 0; k < sigmaSize; k++) {
                    double num = 0;
                    double denom = 0;

                    for (int t = 0; t <= T - 1; t++) {
                        double g = gamma(i, t, o, fwd, bwd);
                        num += g * (k == o[t] ? 1 : 0);
                        denom += g;
                    }
                    b1[i][k] = divide(num, denom);
                }
            }

            pi = pi1;
            a = a1;
            b = b1;

        }

        // return gamma
        double[] gam = new double[o.length];
        for (int t = 0 ; t < o.length ; t++) {
            gam[t] = gamma(0, t, o, fwd, bwd);
        }

        /*
        // print gamma
        String st = "";
        for (int i = 0 ; i < gam.length ; i++) {
            st += gam[i] + " ";
        }
        logM("gam: " + st);
        */

        return gam;

    }


    /** calculation of Forward-Variables f(i,t) for state i at time
     t for output sequence O with the current HMM parameters
     @param o the output sequence O
     @return an array f(i,t) over states and times, containing
     the Forward-variables.
     */
    public double[][] forwardProc(int[] o) {

        // fwd now contains log of its true value
        // a and b are NOT in log form

        int T = o.length;
        double[][] fwd = new double[numStates][T];

        /* initialization (time 0) */
        for (int i = 0; i < numStates; i++) {
            fwd[i][0] = multLogs(logf(pi[i]), logf(b[i][o[0]]));//pi[i] * b[i][o[0]];
            //System.out.println("pi" + i + ": " + pi[i]);
        }

        /* induction */
        for (int t = 0; t <= T-2; t++) {
            for (int j = 0; j < numStates; j++) {
                fwd[j][t+1] = log0; //0;
                for (int i = 0; i < numStates; i++) {
                    fwd[j][t+1] = addLogs(fwd[j][t+1], multLogs(fwd[i][t], logf(a[i][j]))); //+= (fwd[i][t] * a[i][j]);
                }
                //System.out.println("b: " + log(b[j][o[t+1]]));
                fwd[j][t+1] = multLogs(fwd[j][t+1], logf(b[j][o[t + 1]])); //*= b[j][o[t+1]];
            }
        }

        return fwd;
    }

    /** calculation of  Backward-Variables b(i,t) for state i at time
     t for output sequence O with the current HMM parameters
     @param o the output sequence O
     @return an array b(i,t) over states and times, containing
     the Backward-Variables.
     */
    public double[][] backwardProc(int[] o) {

        // bwd now contains log of its true value
        // a and b are NOT in log form

        int T = o.length;
        double[][] bwd = new double[numStates][T];

    /* initialization (time 0) */
        for (int i = 0; i < numStates; i++)
            bwd[i][T-1] = 0;//1;

    /* induction */
        for (int t = T - 2; t >= 0; t--) {
            for (int i = 0; i < numStates; i++) {
                bwd[i][t] = log0;//0;
                for (int j = 0; j < numStates; j++)
                    bwd[i][t] = addLogs(bwd[i][t], multLogs(multLogs(bwd[j][t+1], logf(a[i][j])), logf(b[j][o[t + 1]])));//+= (bwd[j][t+1] * a[i][j] * b[j][o[t+1]]);
            }
        }

        return bwd;
    }

    /** calculation of probability P(X_t = s_i, X_t+1 = s_j | O, m).
     @param t time t
     @param i the number of state s_i
     @param j the number of state s_j
     @param o an output sequence o
     @param fwd the Forward-Variables for o
     @param bwd the Backward-Variables for o
     @return P
     */
    public double p(int t, int i, int j, int[] o, double[][] fwd, double[][] bwd) {

        // inputs fwd, bwd are log form
        // output is NOT log form

        double num;
        if (t == o.length - 1)
            num = multLogs(fwd[i][t], logf(a[i][j])); //fwd[i][t] * a[i][j]; (log form)
        else
            num = multLogs(multLogs(fwd[i][t], logf(a[i][j])), multLogs(logf(b[j][o[t + 1]]), bwd[j][t+1])); //fwd[i][t] * a[i][j] * b[j][o[t+1]] * bwd[j][t+1]; (log form)

        double denom = log0;//0;

        for (int k = 0; k < numStates; k++)
            denom = addLogs(denom, multLogs(fwd[k][t], bwd[k][t])); //+= (fwd[k][t] * bwd[k][t]); (log form)

        return divideLogs(num, denom); //divide(num, denom);
    }

    /** computes gamma(i, t) */
    public double gamma(int i, int t, int[] o, double[][] fwd, double[][] bwd) {

        // inputs fwd, bwd are log form
        // output is NOT log form

        double num = multLogs(fwd[i][t], bwd[i][t]); //fwd[i][t] * bwd[i][t]; (log form)
        double denom = log0; //0; (log form)

        for (int j = 0; j < numStates; j++)
            denom = addLogs(denom, multLogs(fwd[j][t], bwd[j][t])); //+= fwd[j][t] * bwd[j][t]; (log form)

        return divideLogs(num, denom); //divide(num, denom); (output not log form)
    }

    /** divides two doubles. 0 / 0 = 0! */
    public double divide(double n, double d) {
        if (n == 0)
            return 0;
        else
            return n / d;
    }

    /** Add logs
     * Given log(a) and log(b), output log(a+b)
     * Constant log0
     */
    public double addLogs(double loga, double logb) {
        if (loga == log0) {
            return logb;
        }
        if (logb == log0) {
            return loga;
        }
        if (loga > logb) {
            return loga + logf(1 + Math.exp(logb - loga));
        }
        return logb + logf(1 + Math.exp(loga - logb));
    }

    /** Multiply logs
     * Given log(a) and log(b), output log(a*b)
     * Constant log0
     */
    public double multLogs(double loga, double logb) {
        if (loga == log0) {
            return log0;
        }
        if (logb == log0) {
            return log0;
        }
        return loga + logb;
    }

    /** Divide logs
     * Given log(a) and log(b), output a/b
     * Output NOT in log form
     */
    public double divideLogs(double loga, double logb) {
        if (loga == log0) {
            return 0;
        }
        if (logb == log0) {
            return 0;  // divide by zero
        }
        return Math.exp(loga - logb);
    }

    /** Log function
     * Uses log0 constant instead of -infinity
     */
    public double logf(double a) {
        if (a == 0) {
            return log0;
        }
        return Math.log(a);
    }


    // Log message
    public void logM(String s) {
        try {
            log(s);
        }
        catch(Exception e) {
            System.out.println(s);
        }

    }

    // Print 2D array
    public void printArray(double[][] arr) {
        for (int i = 0 ; i < arr.length ; i++) {
            String str = "";
            for (int j = 0 ; j < arr[i].length ; j++) {
                str += arr[i][j];
                if (j < arr[i].length - 1) {
                    str += " ";
                }
            }
            logM(str);
        }
    }

    // Make sure rows of matrix sum to 1
    public static void normalizeArray(double[][] arr) {
        for (int i = 0 ; i < arr.length ; i++) {
            double sum = 0;
            for (int j = 0 ; j < arr[i].length ; j++) {
                sum += arr[i][j];
            }
            for (int j = 0 ; j < arr[i].length ; j++) {
                arr[i][j] /= sum;
            }
        }
    }

    // Make sure rows of matrix sum to 1
    public static void normalizeArray3D(double[][][] arr) {
        for (int i = 0 ; i < arr.length ; i++) {
            double sum = 0;
            for (int j = 0 ; j < arr[i].length ; j++) {
                for (int k = 0 ; k < arr[i][j].length ; k++) {
                    sum += arr[i][j][k];
                }
            }
            for (int j = 0 ; j < arr[i].length ; j++) {
                for (int k = 0 ; k < arr[i][j].length ; k++) {
                    arr[i][j][k] /= sum;
                }
            }
        }
    }

}
