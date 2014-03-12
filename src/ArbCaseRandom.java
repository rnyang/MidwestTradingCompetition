import java.util.Random;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


public class ArbCaseRandom extends AbstractExchangeArbCase implements ArbCase {
	
	Random r = new Random();
	Quote[] marketQuotes;
	Quote[] currentQuotes;

	public ArbCase getArbCaseImplementation() {
		return this;
	}

	public void addVariables(IJobSetup setup) {}

	public void initializeAlgo(IDB dataBase) {
		
	}

	public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {}

	public void positionPenalty(int clearedQuantity, double price) {}

	public void newTopOfBook(Quote[] quotes) {
		marketQuotes = quotes;
	}

	public Quote[] refreshQuotes() {
		int multiplier = r.nextInt(5);
		boolean positive = r.nextBoolean();
		boolean update = r.nextBoolean();
		if (update) {
			Quote[] quotes = new Quote[2];
			double effect = 0.01 * multiplier;
			effect = (positive) ? multiplier : -multiplier;
			quotes[1] = new Quote(marketQuotes[1].exchange, marketQuotes[1].bidPrice * (1 - effect), marketQuotes[1].askPrice * (1 + effect));
			quotes[2] = new Quote(marketQuotes[2].exchange, marketQuotes[2].bidPrice * (1 - effect), marketQuotes[2].askPrice * (1 + effect));
			currentQuotes = quotes;
		}
		return currentQuotes;
	}

}
