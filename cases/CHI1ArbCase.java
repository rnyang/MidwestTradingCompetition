//CHI1 Case 2: HFT Arbitrage

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class CHI1ArbCase extends AbstractExchangeArbCase implements ArbCase {


	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;

	int position;
	double[] desiredRobotPrices = new double[2];
	double[] desiredSnowPrices = new double[2];
	int ticks, localTicks; //number of iterations
	double myAvgBid, myAvgAsk;
	int localNumBuys, localNumSells, totalSpentBuy, totalSpentSell, localSpentBuy, localSpentSell, totalNumBuys, totalNumSells;

	public void addVariables(IJobSetup setup) {
		// Registers a variable with the system.
		//setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		//myDatabase = database;
		position = 0;
		ticks = 0;
		myAvgBid = 0;
		myAvgAsk = 0;
		localNumBuys = 0;
		localNumSells = 0;
		localTicks = 0;
		totalSpentBuy = 0;
		localSpentBuy = 0;
		totalSpentSell = 0;
		localSpentSell = 0;
		totalNumBuys = 0;
		totalNumSells = 0;
	}


	public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
		log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);


		if(algoside == AlgoSide.ALGOBUY){
			position += 1;

			totalSpentBuy+=price;
			localSpentBuy+=price;
			localNumBuys++;
			totalNumBuys++;
			myAvgBid = localSpentBuy/localNumBuys; //clears whenever position is flat again
		}else{
			position -= 1;

			totalSpentSell+=price;
			localSpentSell+=price;
			localNumSells++;
			totalNumSells++;
			myAvgAsk = localSpentSell/localNumSells;
		}
		

		log("Current ticks: " + ticks + "; Current position: " + position);
		log(" localNumBuys: " + localNumBuys + "; myAvgBid: " + myAvgBid + "; localNumSells: " + localNumSells + "; myAvgAsk: " + myAvgAsk);
		log(" totalSpentBuy: " + totalSpentBuy + "; totalSpentSell: " + totalSpentSell);
		log(" totalNumBuys: " + totalNumBuys + "; totalNumSells: " + totalNumSells);
	}


	public void positionPenalty(int clearedQuantity, double price) {
		log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		position -= clearedQuantity;
	}

	//feeds in your bid and ask to market
	public void newTopOfBook(Quote[] quotes) {
		for (Quote quote : quotes) {
			log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
		}

		ticks++; //increment counter

		double RBid = quotes[0].bidPrice;
		double RAsk = quotes[0].askPrice;
		double SBid = quotes[1].bidPrice;
		double SAsk = quotes[1].askPrice;
		double myRBid = -1, myRAsk = -1, mySBid = -1, mySAsk = -1;



		if (ticks < 70)
		{
			//build your starting hand.  starting strategy is start out selling.
			if (localTicks < 2)
			{

				//myRBid = RAsk;
				myRAsk = RBid;

				//mySBid = SAsk;
				mySAsk = SBid;	
				localTicks++;
			}
		}
		
		if (ticks<80){

			if (position > 0)
			{
				//sell off at a profit
				if (RBid > myAvgBid)
				{
					myRAsk = RBid;
				}
				if (SBid > myAvgBid)
				{
					mySAsk = SBid;
				}
			}

			else if (position < 0)
			{
				//buy at a profit
				if (RAsk < myAvgAsk)
					myRBid = RAsk;
				if (SAsk < myAvgAsk)
					mySBid = SAsk;
			}

		}
		
		if (position == 0){
			
			//clear the records because you're flat again
			myAvgBid = 0;
			myAvgAsk = 0;
			localNumBuys = 0;
			localNumSells = 0;
			localTicks = 0;
			localSpentBuy = 0;
			localSpentSell = 0;
		}
		
		if (ticks>=80)  //time's almost up - start clearing your position.  aggressive buying/selling portion
		{
			if (position > 0){ //need to sell
				myRAsk = RBid;
				mySAsk = SBid;
			}
			else if (position < 0){
				myRBid = RAsk;
				mySAsk = RAsk;
			}
		}

		//arbitrage - sell robot high, buy snow low
		if (RBid > SAsk)
		{

			myRAsk = RBid; //my ask = MARKET BID

			mySBid = SAsk;
			log("ARBITRAGE");
		}

		if (SBid > RAsk)
		{
			myRBid = RAsk; //buy robot low

			mySAsk = SBid; //sell snow high
			log("ARBITRAGE");
		}

		
		//if any of these variables were never explicitly changed, that means that you don't want that type of trade to execute
		//so price outside the markets
		if (myRBid == -1)
			myRBid = RAsk - 10000;
		if (myRAsk == -1)
			myRAsk = RBid + 10000;
		if (mySBid == -1)
			mySBid = SAsk - 10000;
		if (mySAsk == -1)
			mySAsk = SBid + 10000;

		desiredRobotPrices[0] = myRBid;
		desiredRobotPrices[1] = myRAsk;
		desiredSnowPrices[0] = mySBid;
		desiredSnowPrices[1] = mySAsk;

	}

	//feeds in your desired quotes to market
	public Quote[] refreshQuotes() {
		Quote[] quotes = new Quote[2];
		quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
		quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
		return quotes;
	}


	public ArbCase getArbCaseImplementation() {
		return this;
	}


}
