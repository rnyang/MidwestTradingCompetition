

import java.util.ArrayList;
import java.util.List;

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

public class CHI2OptionCase extends AbstractOptionsCase implements OptionsCase {
    int counter = 0;

    double[] bids = new double[1000];
    double[] asks = new double[1000];

    double[] minDeltas = new double[1000];
    double[] minGammas = new double[1000];
    double[] minVegas = new double[1000];
    double[] maxDeltas = new double[1000];
    double[] maxGammas = new double[1000];
    double[] maxVegas = new double[1000];

    double[] deltas = new double[1000];
    double[] vegas = new double[1000];
    double[] gammas = new double[1000];

    double[] vols = new double[1000];

    int[] quants = new int[1000];

	private IDB myDatabase;
	int factor;
    int strike;
    int month;
	private List<String> knownSymbols = new ArrayList<String>();


    public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
        setup.addVariable("strike", "strike price", "int", "100");
        setup.addVariable("month", "expiration month", "int", "5");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor");
        strike = getIntVar("strike");
        month = getIntVar("month");
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		knownSymbols.add(idSymbol);
		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
        bids[counter] = bid;
        asks[counter] = ask;
	}

	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		log("I received an admin message!");
        minDeltas[counter] = msg.minDelta;
        minGammas[counter] = msg.minGamma;
        minVegas[counter] = msg.minVega;
        maxDeltas[counter] = msg.maxDelta;
        maxGammas[counter] = msg.maxGamma;
        maxVegas[counter] = msg.maxVega;
	}

	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");
        deltas[counter] = msg.delta;
        vegas[counter] = msg.vega;
        gammas[counter] = msg.gamma;
	}
	
	public void newVolUpdate(VolUpdate msg) {
		log("I received a vol update message!");
        vols[counter] = msg.impliedVol;
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		log("Placing orders");
		OrderInfo[] orders = new OrderInfo[knownSymbols.size()];
		for (int i = 0; i < knownSymbols.size(); i++) {
			String symbol = knownSymbols.get(i);
            // calculate theoretical greeks
            // ((100-counter)+(month-5)*30) is the time period, divided by 365 to get t
            double g = Optionsutil.calculateGamma(((bids[counter]+asks[counter])/2),strike, ((100-counter)+(month-5)*30)/365, 0.01 , vols[counter]);
            double v = Optionsutil.calculateVega(((bids[counter]+asks[counter])/2), strike, ((100-counter)+(month-5)*30)/365, 0.01 , vols[counter]);
            double d = Optionsutil.calculateDelta(((bids[counter]+asks[counter])/2),strike, ((100-counter)+(month-5)*30)/365, 0.01 , vols[counter]);
            double c = Optionsutil.Call((bids[counter]+asks[counter])/2,strike, ((100-counter)+(month-5)*30)/365, 0.01 , vols[counter]);
            // set our "goal" greeks as either the ForecastMessage, or the limit if Forecast exceeds limit
            // first case, if all greeks within limit
            if((minGammas[counter] < gammas[counter]) && (gammas[counter] < maxGammas[counter])&&
                    (minVegas[counter] <vegas[counter]) && (vegas[counter] < maxVegas[counter])) {
                double goalgamma = gammas[counter];
                double goalvega = vegas[counter];
                int gammaquant = (int)(goalgamma/v);
                int vegaquant = (int)(goalvega / v);
                if((vegaquant*g > maxGammas[counter])&&(minVegas[counter] < gammaquant*v)&&(gammaquant*v < maxVegas[counter]))
                    quants[counter] = gammaquant;
                if((gammaquant*v > maxVegas[counter])&&(minGammas[counter] < vegaquant*g)&&(vegaquant*g <maxGammas[counter]))
                    quants[counter] = vegaquant;
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, c, quants[counter]);
            }
            // second case, if gamma less than lower limit
            if((minGammas[counter] > gammas[counter]) && (maxVegas[counter] >vegas[counter]) && (minVegas[counter] < vegas[counter])) {
                double goalgamma = minGammas[counter];
                double goalvega =vegas[counter];
                int gammaquant = (int)(goalgamma/v);
                int vegaquant = (int)(goalvega/v);
                if((vegaquant*g > maxGammas[counter])&&(minVegas[counter] < gammaquant*v)&&(gammaquant*v <maxVegas[counter]))
                    quants[counter] = gammaquant;
                if((gammaquant*v >maxVegas[counter])&&(minGammas[counter] < vegaquant*g)&&(vegaquant*g <maxGammas[counter]))
                    quants[counter] = vegaquant;
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, c, quants[counter]);
            }
            // third case, if gamma larger than upper limit
            if((maxGammas[counter] < gammas[counter]) && (maxVegas[counter] > vegas[counter]) &&
                    (minVegas[counter] <vegas[counter])) {
                double goalgamma = maxGammas[counter];
                double goalvega = vegas[counter];
                int gammaquant = (int)(goalgamma/v);
                int vegaquant = (int)(goalvega/v);
                if((vegaquant*g > maxGammas[counter])&&(minVegas[counter] < gammaquant*v)&&(gammaquant*v <maxVegas[counter]))
                    quants[counter] = gammaquant;
                if((gammaquant*v >maxGammas[counter])&&(minGammas[counter] < vegaquant*g)&&(vegaquant*g <maxGammas[counter]))
                    quants[counter] = vegaquant;
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, c, quants[counter]);
            }
            // fourth case, if vega less than lower limit
            if((minVegas[counter] > vegas[counter]) && (maxGammas[counter] > gammas[counter]) &&
                    (minGammas[counter] <gammas[counter])) {
                double goalgamma = gammas[counter];
                double goalvega = minVegas[counter];
                int gammaquant = (int)(goalgamma/v);
                int vegaquant = (int)(goalvega/v);
                if((vegaquant*g > maxGammas[counter])&&(minVegas[counter] < gammaquant*v)&&(gammaquant*v <maxVegas[counter]))
                    quants[counter] = gammaquant;
                if((gammaquant*v > maxVegas[counter])&&(minGammas[counter] < vegaquant*g)&&(vegaquant*g <maxGammas[counter]))
                    quants[counter] = vegaquant;
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, c, quants[counter]);
            }
            // fifth case, if vega larger than upper limit
            if((maxGammas[counter] < vegas[counter]) && (maxGammas[counter] > gammas[counter]) &&
                    (minGammas[counter] < gammas[counter])) {
                double goalgamma = gammas[counter];
                double goalvega = maxVegas[counter];
                int gammaquant = (int)(goalgamma/v);
                int vegaquant = (int)(goalvega/v);
                if((vegaquant*g > maxGammas[counter])&&(minVegas[counter] < gammaquant*v)&&(gammaquant*v <maxVegas[counter]))
                    quants[counter] = gammaquant;
                if((gammaquant*v > maxVegas[counter])&&(minGammas[counter] < vegaquant*g)&&(vegaquant*g <maxGammas[counter]))
                    quants[counter] = vegaquant;
                orders[i] = new OrderInfo(symbol, OrderSide.BUY, c, quants[counter]);
            }
		}
        counter++;
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
