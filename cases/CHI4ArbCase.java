

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class CHI4ArbCase extends AbstractExchangeArbCase implements ArbCase {
	
		
	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;
	double factor;
	double Ceiling;

	int time = 0;
	int position = 0;
	double PnL = 0;
	double net = 0;
	double TotalBuyPrice = 0;
	double TotalSellPrice = 0;
	double AverageBuyPrice = 0;
	double AverageSellPrice = 0;
	int NumBought;
	int NumSold;
	double n = 0;
	double m = 0;
	double[] desiredRobotPrices= new double[2];
	double[] desiredSnowPrices = new double[2];
	//double[] snowPrices = new double [250];
	//double[] robotPrices = new double [250];
	int index;
	
	
	public void addVariables(IJobSetup setup) {
		// Registers a variable with the system.
		setup.addVariable("someFactor", "aggressiveness factor", "double", "2");
		setup.addVariable("someFactor2", "Profit Cut-Off", "double", "50");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getDoubleVar("someFactor"); 
		Ceiling = getDoubleVar("someFactor2");
	}

	public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
		log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);

		if(algoside == AlgoSide.ALGOBUY){
			position += 1;
			NumBought +=1;
			PnL -= price;
			TotalBuyPrice += price;
			
		}else{
			position -= 1;
			NumSold += 1;
			PnL += price;
			TotalSellPrice +=  price;
		}
		
	}


	public void positionPenalty(int clearedQuantity, double price) {
		log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		position -= clearedQuantity;
	}

	public void newTopOfBook(Quote[] quotes) {
		for (Quote quote : quotes) {
			log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
		n = ((quotes[1].askPrice - quotes[1].bidPrice)/(2*factor));
		m = ((quotes[0].askPrice - quotes[0].bidPrice)/(2*factor));
		AverageBuyPrice = (TotalBuyPrice-TotalSellPrice)/position;
		AverageSellPrice = (TotalBuyPrice-TotalSellPrice)/position;
		time = time + 1;
		if (position > 0){
			net = PnL + position*Math.max(quotes[0].bidPrice, quotes[1].bidPrice);
		}
		if (position < 0){
			net = PnL + position*Math.min(quotes[1].askPrice,quotes[0].askPrice);
		}
		//index = (int) (time/2);
		//snowPrices[index] =  (quotes[1].askPrice + quotes[1].bidPrice) / 2;
		//robotPrices[index] = (quotes[0].askPrice + quotes[0].bidPrice) / 2;
		log("The time is: " + time);
		log("My current position is: " + position);
		log("My current PnL is:" + PnL);
		log("My net PnL is: " + net);
		}
		if (position == 0){
			log("Position Buy Zero");
			desiredSnowPrices[1] = quotes[1].askPrice - n;
			desiredRobotPrices[1] = quotes[0].askPrice - m;
		}
		if(position == 0){
			log("Position Sell Zero");
			desiredSnowPrices[0] = quotes[1].bidPrice + n;
			desiredRobotPrices[0] = quotes[0].bidPrice + m;
		}
		
		else
		{
		log("Position Not Zero");
		if ((quotes[0].askPrice + quotes[0].bidPrice)/2 < (quotes[1].askPrice + quotes[1].bidPrice)/2){
			log("Arbitrage! Robot -> Snow");
			if(quotes[0].bidPrice < AverageSellPrice){
			desiredSnowPrices[1] = quotes[1].askPrice - n;
			desiredSnowPrices[0] = quotes[1].bidPrice - 10*n;}
			else{
			desiredSnowPrices[1] = quotes[1].askPrice + 10*n;
			desiredSnowPrices[0] = quotes[1].bidPrice - 10*n;}
			if(quotes[1].askPrice > AverageBuyPrice){
			desiredRobotPrices[1] = quotes[0].askPrice + 10*m;
			desiredRobotPrices[0] = quotes[0].bidPrice + m;}
			else{
			desiredRobotPrices[1] = quotes[0].askPrice + 10*m;
			desiredRobotPrices[0] = quotes[0].bidPrice - 10*m;
			}
		}
		
		if ((quotes[0].askPrice + quotes[0].bidPrice)/2 > (quotes[1].askPrice + quotes[1].bidPrice)/2){
			log("Arbitrage! Snow -> Robot");
			if(quotes[1].bidPrice < AverageSellPrice){
				desiredSnowPrices[1] = quotes[1].askPrice + 10*n;
				desiredSnowPrices[0] = quotes[1].bidPrice + n;}
			else{
				desiredSnowPrices[1] = quotes[1].askPrice + 10*n;
				desiredSnowPrices[0] = quotes[1].bidPrice - 10*n;}
			if (quotes[0].askPrice > AverageBuyPrice){
				desiredRobotPrices[1] = quotes[0].askPrice - m;
				desiredRobotPrices[0] = quotes[0].bidPrice - 10*m;}
			else{
				desiredRobotPrices[1] = quotes[0].askPrice + 10*m;
				desiredRobotPrices[0] = quotes[0].bidPrice - 10*m;}
		}
		
		
		if (position > 183){
			desiredRobotPrices[0] = quotes[0].bidPrice - 10*m;
			desiredRobotPrices[1] = quotes[0].askPrice - m*(2.1/2.01);
			desiredSnowPrices[0] = quotes[1].bidPrice - 10*n;
			desiredSnowPrices[1] = quotes[1].askPrice - n*(2.1/2.01);
		}
		if (position < -183){
			desiredRobotPrices[0] = quotes[0].bidPrice + m;
			desiredRobotPrices[1] = quotes[0].askPrice + 10*m;
			desiredSnowPrices[0] = quotes[1].bidPrice + n;
			desiredSnowPrices[1] = quotes[1].askPrice + 10*n;
		}}
		if (net > Ceiling){
			if(position < 0){
		
			desiredRobotPrices[0] = quotes[0].bidPrice +m;
			desiredRobotPrices[1] = quotes[0].askPrice + 100*m;
			desiredSnowPrices[0] = quotes[1].bidPrice + n;
			desiredSnowPrices[1] = quotes[1].askPrice + 100*m;
		}
			if(position > 0){
				desiredRobotPrices[0] = quotes[0].bidPrice - 100*m;
				desiredRobotPrices[1] = quotes[0].askPrice - m;
				desiredSnowPrices[0] = quotes[1].bidPrice - 100*n;
				desiredSnowPrices[1] = quotes[1].askPrice - n;
			}}}

	

	private void log(int n2) {
		// TODO Auto-generated method stub
		
	}

	public Quote[] refreshQuotes() {
		Quote[] quotes = new Quote[2];
		quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
		quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
		log("My ROBOT spread is: " + desiredRobotPrices[0] + " and " + desiredRobotPrices[1]);
		log("My SNOW spread is: " + desiredSnowPrices[0] + " and " + desiredSnowPrices [1]);
		log("My position is: " + position);
		log("My Average Buy Price is " + AverageBuyPrice);
		log("My Average Sell Price is " + AverageSellPrice);
		return quotes;
	}


	public ArbCase getArbCaseImplementation() {
		return this;
	}

}
