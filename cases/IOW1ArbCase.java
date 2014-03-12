import java.util.Arrays;
import java.util.LinkedList;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class IOW1ArbCase extends AbstractExchangeArbCase implements ArbCase {

	// Note...the IDB will be used to save data to the hard drive and access it
	// later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;
	int factor;

	double totalPurchased = 0;
	double totalSold = 0;
	int position;

	int round = 1;

	double ex_mem() {
		return round == 3 ? .225 : .1;
	}

	double underlying_vol() {
		return round == 1 ? .3 : round == 2 ? 1 : .2;
	}

	double spread_mean() {
		return round == 1 ? 1 : round == 2 ? 2 : .2;
	}

	double spread_std() {
		return round == 3 ? .2 : .5;
	}

	double shock_std() {
		return round == 3 ? .2 : .5;
	}

	int ticks = 0;
	LinkedList<Double> snowMeds = new LinkedList<Double>();
	LinkedList<Double> robotMeds = new LinkedList<Double>();

	double[][] covD = new double[100][100];

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

	public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
		log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);

		if (algoside == AlgoSide.ALGOBUY) {
			position += 1;
			totalPurchased += price;
		} else {
			position -= 1;
			totalSold += price;
		}
	}

	public void positionPenalty(int clearedQuantity, double price) {
		log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		position -= clearedQuantity;
		totalSold += price * clearedQuantity;
	}

	public void newTopOfBook(Quote[] quotes) {
		ticks++;
		snowMeds.add((quotes[0].askPrice + quotes[0].bidPrice) / 2);
		robotMeds.add((quotes[1].askPrice + quotes[1].bidPrice) / 2);
		log("ROBOT: BID: " + quotes[1].bidPrice + " ASK: " + quotes[1].askPrice + " SNOW: BID: " + quotes[0].bidPrice + " ASK: " + quotes[0].askPrice);
		log("Total Purchased: " + totalPurchased + " Total Sold: " + totalSold + " Total Position: " + position);
	}

	public Quote[] refreshQuotes() {
		double[] expectedDifferences = new double[4];
		double T = 0;
		for (int i = 0; i < 4; i++) {
			T += expectedDifferences[i] = calcExpectedDiff(ticks + i);
		}
		log(Arrays.toString(expectedDifferences));
		T /= 4;

		Quote[] quotes = new Quote[2];
		double modUp = 5 * underlying_vol();
		double modDown = 5 * underlying_vol();
		if (position > 175)
			modDown *= 2;
		else if (position < -175)
			modUp *= 2;
		if (Math.abs(T) < .5) {
			modUp *= 2;
			modDown *= 2;
		}
		if (T > 0) {
			log("new bid: " + (snowMeds.getLast() - spread_mean() - modDown) + " new ask: " + (robotMeds.getLast() + spread_mean() + modUp));
			quotes[0] = new Quote(Exchange.ROBOT, snowMeds.getLast() - spread_mean() - modDown, robotMeds.getLast() + spread_mean() + modUp);
			quotes[1] = new Quote(Exchange.SNOW, snowMeds.getLast() - spread_mean() - modDown, robotMeds.getLast() + spread_mean() + modUp);
		} else {
			log("new bid: " + (robotMeds.getLast() - spread_mean() - modDown) + " new ask: " + (snowMeds.getLast() + spread_mean() + modUp));
			quotes[0] = new Quote(Exchange.ROBOT, robotMeds.getLast() - spread_mean() - modDown, snowMeds.getLast() + spread_mean() + modUp);
			quotes[1] = new Quote(Exchange.SNOW, robotMeds.getLast() - spread_mean() - modDown, snowMeds.getLast() + spread_mean() + modUp);
		}
		return quotes;
	}

	double calcExpectedDiff(int t) {
		double k = ex_mem();
		double T = 0;
		for (int i = 1; i <= 9; i++) {
			int D = t - i - 1;
			double ex;
			if (D < 0)
				break;
			if (D >= ticks) {
				ex = calcExpectedDiff(D);
			} else {
				ex = robotMeds.get(D) - snowMeds.get(D);
			}
			T += ex;
		}
		return k * T;
	}

	public ArbCase getArbCaseImplementation() {
		return this;
	}

}
