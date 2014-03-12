import java.util.*;

import com.optionscity.freeway.api.InstrumentDetails;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.util.Pair;
import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class ILL1OptionCaseImplementation extends AbstractOptionsCase implements OptionsCase {

    private enum Greeks {
        DELTA,
        GAMMA,
        VEGA,
        THETA
    }

    private final NormalDistribution dist = new NormalDistribution(0.0, 1.0);

    private IDB myDatabase;
    int factor;

    private double PnL = 0;
    private double currentVol = 0.15;
    private final double INTEREST_RATE = .01;
    // Position Greeks
    private double positionDelta = 0.0;
    private double positionVega = 0.0;
    private double positionGamma = 0.0;
    private double positionTheta = 0.0;
    // Last bid/ask on the underlying
    private double lastUnderlyingBid = 0.0;
    private double lastUnderlyingAsk = 0.0;
    private String underlyingSymbol = null;

    private final double THRESHOLD = 5000.0;


    private Set<String> knownSymbols = new HashSet<String>();
    // Messages we need for targets and limits
    private RiskMessage limits = null;
    private ForecastMessage targets = null;


    private int timeRemaining = 100;
    private final HashMap<String, Pair<Double, Double>> bidAsks = new HashMap<String, Pair<Double, Double>>();
    private final HashMap<String, Integer> positions = new HashMap<String, Integer>();

    private final HashMap<String, Integer> desiredPositions = new HashMap<String, Integer>();

    public void addVariables(IJobSetup setup) {
        setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
        setup.addVariable("initialLimit", "position limits used when we don't have limits", "int", "100");
    }

    public void initializeAlgo(IDB database) {
        // Databases can be used to store data between rounds
        myDatabase = database;

        // helper method for accessing declared variables
        factor = getIntVar("someFactor");
    }

    /**
     * Update the bid and ask we have for this particular symbol.
     *
     * @param idSymbol The ID symbol for this instrument.
     * @param bid      The bid price for this instrument.
     * @param ask      The ask price for this instrument.
     */
    public void newBidAsk(String idSymbol, double bid, double ask) {
        knownSymbols.add(idSymbol);
        log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
        InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
        if (details.type == InstrumentDetails.Type.EQUITY) {
            log("Receiving equity bid/ask");
            lastUnderlyingBid = bid;
            lastUnderlyingAsk = ask;
            underlyingSymbol = idSymbol;
        }
        bidAsks.put(idSymbol, new Pair<Double, Double>(bid, ask));
        if (!positions.containsKey(idSymbol)) {
            positions.put(idSymbol, 0);
        }
    }

    public void newRiskMessage(RiskMessage msg) {
        log("Received a new risk message with gamma limit from " + msg.minGamma + " to " + msg.maxGamma);
        limits = msg;
        computeTargets();
    }

    public void orderFilled(String idSymbol, double price, int quantity) {
        log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
        // Track our current position
        Integer currentPosition = positions.get(idSymbol);
        int curPos;
        if (currentPosition == null)
            curPos = quantity;
        else
            curPos = currentPosition + quantity;
        log("Our position for " + idSymbol + " is " + curPos);
        positions.put(idSymbol, currentPosition);
    }

    private double calculatePositionGreek(Greeks greek) {
        double total = 0.0;
        for (String symbol : knownSymbols) {
            InstrumentDetails details = instruments().getInstrumentDetails(symbol);
            // Grab the bid and ask for the current symbol.
            Pair<Double, Double> bidAsk = bidAsks.get(symbol);
            // Get our position in this symbol.
            Integer position = positions.get(symbol);
            if (position != null) {
                // Figure out how much time is left.
                double timeProportion = timeRemaining / 365.0;
                // If it's a call option most Greeks apply.
                if (details.type == InstrumentDetails.Type.CALL) {
                    double midPrice = (bidAsk.getFirst() + bidAsk.getSecond()) / 2;
                    switch (greek) {
                        case DELTA:
                            total += Optionsutil.calculateDelta(midPrice, details.strikePrice, timeProportion,
                                    INTEREST_RATE, currentVol) * position;
                            break;
                        case GAMMA:
                            total += Optionsutil.calculateGamma(midPrice, details.strikePrice, timeProportion,
                                    INTEREST_RATE, currentVol) * position;
                            break;
                        case VEGA:
                            total += Optionsutil.calculateVega(midPrice, details.strikePrice, timeProportion,
                                    INTEREST_RATE, currentVol) * position;
                            break;
                        case THETA:
                            double d1 = calculateD1(details.strikePrice, currentVol, midPrice, timeProportion,
                                    INTEREST_RATE);
                            total += theta(d1, midPrice, currentVol, timeProportion, INTEREST_RATE,
                                    details.strikePrice) * position;
                            break;
                        default:
                    }
                } else if (details.type == InstrumentDetails.Type.EQUITY) {
                    switch (greek) {
                        case DELTA:
                            total += position.doubleValue();
                            break;
                        default:
                    }
                }
            }
        }
        return total;
    }

    public void newForecastMessage(ForecastMessage msg) {
        log("Received a new forecast message with delta " + msg.delta + ", gamma " + msg.gamma + ", vega " + msg.vega);
        targets = msg;
        computeTargets();
    }

    private void computeTargets() {
        double[] deltas = new double[knownSymbols.size()];
        double[] gammas = new double[knownSymbols.size()];
        double[] vegas = new double[knownSymbols.size()];
        double[] thetas = new double[knownSymbols.size()];
        double[] costs = new double[knownSymbols.size()];
        int i = 0;
        String[] symbolsInOrder = new String[knownSymbols.size()];
        for (String symbol : knownSymbols) {
            symbolsInOrder[i] = symbol;
            InstrumentDetails details = instruments().getInstrumentDetails(symbol);
            Pair<Double, Double> bidAsk = bidAsks.get(symbol);
            double timeProportion = timeRemaining / 365.0;
            double underlyingMid = (lastUnderlyingAsk + lastUnderlyingBid) / 2;
            if (details.type == InstrumentDetails.Type.CALL && bidAsk != null) {
                double mid = (bidAsk.getFirst() + bidAsk.getSecond()) / 2;
                deltas[i] = Optionsutil.calculateDelta(underlyingMid, details.strikePrice, timeProportion, INTEREST_RATE, currentVol);
                gammas[i] = Optionsutil.calculateGamma(underlyingMid, details.strikePrice, timeProportion, INTEREST_RATE, currentVol);
                vegas[i] = Optionsutil.calculateVega(underlyingMid, details.strikePrice, timeProportion, INTEREST_RATE, currentVol);
                double d1 = calculateD1(details.strikePrice, currentVol, underlyingMid, timeProportion, INTEREST_RATE);
                thetas[i] = theta(d1, underlyingMid, currentVol, timeProportion, INTEREST_RATE, details.strikePrice);
                costs[i] = mid;
            } else if (details.type == InstrumentDetails.Type.EQUITY) {
                deltas[i] = 1.0;
                gammas[i] = 0.0;
                vegas[i] = 0.0;
                thetas[i] = 0.0;
                costs[i] = underlyingMid;
            }
            ++i;
        }
        try {
            ArrayList<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
            LinearObjectiveFunction f = new LinearObjectiveFunction(thetas, 0);
            if (limits != null && targets != null) {
                constraints.add(new LinearConstraint(deltas, Relationship.GEQ, limits.minDelta));
                constraints.add(new LinearConstraint(deltas, Relationship.LEQ, limits.maxDelta));
                constraints.add(new LinearConstraint(gammas, Relationship.GEQ, limits.minGamma));
                constraints.add(new LinearConstraint(gammas, Relationship.LEQ, limits.maxGamma));
                constraints.add(new LinearConstraint(vegas, Relationship.GEQ, limits.minVega));
                constraints.add(new LinearConstraint(vegas, Relationship.LEQ, limits.maxVega));
                constraints.add(new LinearConstraint(costs, Relationship.LEQ, THRESHOLD));
                constraints.add(new LinearConstraint(costs, Relationship.GEQ, -THRESHOLD));
                log("Number of constraints is: " + constraints.size());
            } else {
                return;
            }
            log("LP deltas are " + Arrays.toString(deltas));
            log("LP gammas are " + Arrays.toString(gammas));
            log("LP vegas are " + Arrays.toString(vegas));
            log("LP thetas are " + Arrays.toString(thetas));
            log("LP costs are " + Arrays.toString(costs));
            LinearConstraintSet constraintSet = new LinearConstraintSet(constraints);
            PointValuePair solution = new SimplexSolver().optimize(f, constraintSet, new NonNegativeConstraint(false),
                    GoalType.MAXIMIZE);
            // get the solution
            double max = solution.getValue();
            for (int j = 0; j < symbolsInOrder.length; j++) {
                desiredPositions.put(symbolsInOrder[i], (int)Math.round(solution.getPoint()[i]));
            }
            log("The optimal value is: " + max);
            log("The positions are: " + Arrays.toString(solution.getPoint()));
        } catch (Exception e) {
            log(e.getMessage());
            log("No solution was found to the LP!");
        }
    }

    /**
     * We got a new volatility, recalculate our Greeks.
     *
     * @param msg The update of volatility.
     */
    public void newVolUpdate(VolUpdate msg) {
        --timeRemaining;
        currentVol = msg.impliedVol;
        positionDelta = calculatePositionGreek(Greeks.DELTA);
        positionGamma = calculatePositionGreek(Greeks.GAMMA);
        positionTheta = calculatePositionGreek(Greeks.THETA);
        positionVega = calculatePositionGreek(Greeks.VEGA);
    }

    public void penaltyFill(String idSymbol, double price, int quantity) {
        log("Penalty called...oh no!");
        // Penalty handling logic here
    }

    public OrderInfo[] placeOrders() {
        computeTargets();
        // Place a buy order of 100.00 with qty of 10 for every symbol we know of
        // Note: Just a 'dummy' implementation.
        OrderInfo[] orderRet = new OrderInfo[0];
        ArrayList<OrderInfo> orders = new ArrayList<OrderInfo>(knownSymbols.size());
        // Hedge delta
        long sharesToBuy = Math.round(-positionDelta) + Math.round(targets.delta);
        if (sharesToBuy < 0) {
            orders.add(new OrderInfo(underlyingSymbol, OrderSide.SELL, bidAsks.get(underlyingSymbol).getFirst(), (int)Math.abs(sharesToBuy)));
        }
        else if (sharesToBuy > 0) {
            orders.add(new OrderInfo(underlyingSymbol, OrderSide.BUY, bidAsks.get(underlyingSymbol).getSecond(), (int)Math.abs(sharesToBuy)));
        }
        // Now try and go short options
        for (String symbol : knownSymbols) {
            InstrumentDetails details = instruments().getInstrumentDetails(symbol);
            // THe 120 strike
            if (details.strikePrice - 120 < .01 || details.strikePrice - 80 < .01) {
                if (Math.abs(positionVega - limits.minVega) > (limits.minVega / 2)) {
                    orders.add(new OrderInfo(symbol, OrderSide.BUY, bidAsks.get(symbol).getSecond(), 1));
                } else {
                    orders.add(new OrderInfo(symbol, OrderSide.SELL, bidAsks.get(symbol).getFirst(), 1));
                }
            }
        }
        return orders.toArray(orderRet);
    }

    /**
     * Calculate the value of d1 given a strike price. We already know the
     * underlying, the interest rate, and the volatility.
     *
     * @param rate       The interest rate currently.
     * @param strike     The strike price of the underlying instrument.
     * @param vol        The volatility of the underlying instrument.
     * @param time       The time remaining until this option expires.
     * @param underlying The underlying price.
     * @return The d1 value calculated using Black-Scholes.
     */
    private double calculateD1(double strike, double vol, double underlying, double time, double rate) {
        double numerator = Math.log(underlying / strike)
                + ((rate + (vol * vol / 2)) * time);
        return (numerator / (vol * Math.sqrt(time)));
    }

    /**
     * Return the theta for an option.
     *
     * @param d1         The d1 number that we calculate using Black-Scholes.
     * @param underlying The underlying price.
     * @param vol        The volatility of the underlying instrument.
     * @param time       The time remaining until this option expires.
     * @param rate       The interest rate currently.
     * @param strike     The strike price of the underlying instrument.
     * @return The theta of the option.
     */
    private double theta(double d1, double underlying, double vol, double time, double rate, double strike) {
        double d2 = d1 - vol * Math.sqrt(time);
        double intermediate = -underlying * dist.density(d1) * vol / (2 * Math.sqrt(time));
        return intermediate - rate * strike * Math.exp(-rate * time) * dist.cumulativeProbability(d2);
    }


    public OptionsCase getOptionCaseImplementation() {
        return this;
    }

}
