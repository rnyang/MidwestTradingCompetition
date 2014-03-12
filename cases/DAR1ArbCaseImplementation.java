import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.ArrayList;

public class DAR1ArbCaseImplementation extends AbstractExchangeArbCase implements ArbCase {

		private IDB myDatabase;
		int factor;

		int position;
		
		double weightFactor = 0.75;
		
		double baseSpread = 0.2;
		double spreadFactor = 0.25;
		
		double cash = 0, stock = 0, pnl = 0, price = 0;
		int numOrders = 0;
		
		ArrayList<Double> robotBids = new ArrayList<Double>();
		ArrayList<Double> robotAsks = new ArrayList<Double>();
		ArrayList<Double> snowBids = new ArrayList<Double>();
		ArrayList<Double> snowAsks = new ArrayList<Double>();
		
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

			if(algoside == AlgoSide.ALGOBUY){
				position -= 1;
				cash += price;
				
			}else{
				position += 1;
				cash -= price;
			}
			updatePNL();
			numOrders++;
			logStatus();
		}

		private void updateBestPrice() {
			if (position > 0)
				price = (robotBids.get(robotBids.size()-1) < snowBids.get(snowBids.size()-1)) ? snowBids.get(snowBids.size()-1) : robotBids.get(robotBids.size()-1);
			else
				price = (robotAsks.get(robotAsks.size()-1) < snowAsks.get(snowAsks.size()-1)) ? robotAsks.get(robotAsks.size()-1) : snowAsks.get(snowAsks.size()-1);
		}
		
		private void updatePNL() {
			stock = position*price;
			pnl = cash + stock;
		}
		
		private void logStatus() {
			log("Cash: " + cash + ", stock: " + stock + ", position: " + position + ", pnl " + pnl + ", numOrders: " + numOrders);
		}

		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
			cash += price * clearedQuantity;
			updatePNL();
			logStatus();
		}

		public void newTopOfBook(Quote[] quotes) {
			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
			}

			robotBids.add(quotes[0].bidPrice);
			robotAsks.add(quotes[0].askPrice);

			snowBids.add(quotes[1].bidPrice);
			snowAsks.add(quotes[1].askPrice);
			
			updateBestPrice();
			updatePNL();
			logStatus();
		}

		public Quote[] refreshQuotes() {			
			double trueMarketPrice = wMovingAverage(Math.min(robotBids.size(), 10));
			log("I think true market price is: " + trueMarketPrice);
			double[] spread = getSpread();
			log("My spread is [" + spread[0] + ", " +spread[1] + "]");
			double myBid = trueMarketPrice-spread[0];
			double myAsk = trueMarketPrice+spread[1];
			
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, myBid, myAsk);
			quotes[1] = new Quote(Exchange.SNOW, myBid, myAsk);
			for (Quote quote : quotes) {
				log("I sent a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " to " + quote.exchange);
			}
			return quotes;
		}

		private double wMovingAverage(int days) {
			double firstWeight = (1-weightFactor)/(1-Math.pow(weightFactor, days));
			double mean = 0;
			for (int i=0; i < days; i++) {
				double meanRobot = (robotBids.get(robotBids.size()-1-i) + robotAsks.get(robotAsks.size()-1-i))/2;
				double meanSnow = (snowBids.get(snowBids.size()-1-i) + snowAsks.get(snowAsks.size()-1-i))/2;
				double midpoint = (meanRobot + meanSnow)/2;
				mean = mean + midpoint * firstWeight * Math.pow(weightFactor, i);
			}
			return mean;
		}
		
		public ArbCase getArbCaseImplementation() {
			return this;
		}
		
		private double[] getSpread() {
			double robotSpread = robotAsks.get(robotAsks.size()-1) - robotBids.get(robotBids.size()-1);
			double snowSpread = snowAsks.get(snowAsks.size()-1) - snowBids.get(snowBids.size()-1);
			double averageSpread = (robotSpread + snowSpread)/2;
			double bidPremium, askPremium;
			int positionRisk = position/16;
			
			if (positionRisk > 0) {
				askPremium = baseSpread-spreadFactor*positionRisk;
				bidPremium = baseSpread;
				
			} else {
				bidPremium = baseSpread+spreadFactor*positionRisk;
				askPremium = baseSpread;
			}
			if (pnl < 0) {
				bidPremium += 0.05;
				askPremium += 0.05;
			}
			double[] spreads = {averageSpread/2*(1+bidPremium), averageSpread/2*(1+askPremium)};
			return spreads;
		}
}