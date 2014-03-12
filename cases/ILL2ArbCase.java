

import java.util.LinkedList;
import java.util.List;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ILL2ArbCase extends AbstractExchangeArbCase implements ArbCase {
	//macro variables
	//offset variables to be multiplied by prices
	//offsets for arbitrage
	
	
	double STANDARD_BID_OFFSET = 1.02;
	double STANDARD_ASK_OFFSET = .98;
	double ARBITRAGE_CHECK = .02;
	//184 because a maximum of 8 can be boughten in between rounds
	int ARBITRAGE_LIMIT = 192;
	//booleans
	boolean ROBOT_TO_SNOW_ARB;
	boolean SNOW_TO_ROBOT_ARB;
	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;
	int factor;
	int position;
	double[] desiredRobotPrices = new double[]{0, 0};
	double[] desiredSnowPrices = new double[]{0, 0};
	//used to keep track of profit.
	double profit = 0;
	double assets = 0;
	double profitPlusAssets;
	//used to keep track of historic prices
	double averagePrice;
	double numberOfTicks;
	//used to find higher market ask
	boolean SNOW_HIGHER_ASK;
	boolean SNOW_LOWER_BID;
	
	//0 for market going down, 1 for market market staying same, 2 for market going up
	int seed = 0;
	
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
		assets = position * price;
		profitPlusAssets = profit + assets;
		log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside + ", My profit is " + profit + " My position: " + position + " Assets: " + assets + " Total: " + profitPlusAssets);
		if(algoside == AlgoSide.ALGOBUY){
			profit -= price;
			position += 1;
		}else{
			if(position > 0)
			{
				profit += price;
				position -= 1;
			}
		}
	}


	public void positionPenalty(int clearedQuantity, double price) {
		log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		profit += price  * clearedQuantity;
		position -= clearedQuantity;
	}

	
	public void newTopOfBook(Quote[] quotes) {
	
		for (Quote quote : quotes) {
			log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange + " Tick: " + numberOfTicks);
		}
		double robotBidPrice =  quotes[0].bidPrice;
		double robotAskPrice = quotes[0].askPrice;
		double snowBidPrice = quotes[1].bidPrice;
		double snowAskPrice = quotes[1].askPrice;
		adjustAverage(quotes);
		setArbitrage(robotBidPrice, robotAskPrice, snowBidPrice, snowAskPrice);
		setPrices(robotBidPrice, robotAskPrice, snowBidPrice, snowAskPrice);
		adjustPriceForLimit();
	}

	/**
	 * Adjusts the average price
	 * @param quotes the current quotes
	 */
	public void adjustAverage(Quote[] quotes)
	{
		double total = averagePrice * numberOfTicks;
		total += (quotes[0].bidPrice + quotes[0].askPrice + quotes[1].bidPrice + quotes[1].askPrice) / 4;
		numberOfTicks ++;
		averagePrice = total / numberOfTicks;
		
	}
	/**
	 * This function takes the expected bid and askPrice and see if arbitrage exists
	 * @param robotBidPrice
	 * @param robotAskPrice
	 * @param snowBidPrice
	 * @param snowAskPrice
	 */
	private void setArbitrage(double robotBidPrice, double robotAskPrice, double snowBidPrice, double snowAskPrice)
	{
		//see if snow ask price is greater than the robot bid price
			if( snowBidPrice < robotBidPrice )
			{
				SNOW_LOWER_BID = true;
			}
			else
			{
				SNOW_LOWER_BID = false;
			}
			if(snowAskPrice > robotAskPrice)
			{
				SNOW_HIGHER_ASK = true;
			}
			else
			{
				SNOW_HIGHER_ASK = false;
			}
	}
	
	
	/**
	 * Sets the prices for oiur asking.
	 * @param robotBidPrice the robot bid price
	 * @param robotAskPrice the robot ask prices
	 * @param snowBidPrice
	 * @param snowAskPrice
	 */
	private void setPrices(double robotBidPrice, double robotAskPrice, double snowBidPrice, double snowAskPrice)
	{
		double lowerBid;
		double higherAsk;
		double lowerAsk;
		if(SNOW_LOWER_BID)
		{
			lowerBid = snowBidPrice;
		}
		else
		{
			lowerBid = robotBidPrice;
		}
		if(SNOW_HIGHER_ASK)
		{
			higherAsk = snowAskPrice;
			lowerAsk = robotAskPrice;
		}
		else
		{
			higherAsk = robotAskPrice;
			lowerAsk = snowAskPrice;
		}
		//market going up
		if(seed == 2)
		{
			double PERCENT_MARGIN_BID = .30;
			double PERCENT_MARGIN_ASK = .20;
			desiredRobotPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredSnowPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredRobotPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
			desiredSnowPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
		}
		//market staying same
		else if(seed == 1)
		{
			double PERCENT_MARGIN_BID = .20;
			double PERCENT_MARGIN_ASK = .20;
			desiredRobotPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredSnowPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredRobotPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
			desiredSnowPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
		}
		//market going down
		else
		{
			double PERCENT_MARGIN_BID = -.1;
			double PERCENT_MARGIN_ASK = .2;
			desiredRobotPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredSnowPrices[0] = lowerBid + ((higherAsk - lowerBid) * PERCENT_MARGIN_BID);
			desiredRobotPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
			desiredSnowPrices[1] = higherAsk - ((higherAsk - lowerBid) * PERCENT_MARGIN_ASK);
		}
	}
	
	/**
	 * TODO MAKE MACRO VARIABLES
	 * @param ratio the ratio of the higher market to lower market ratio
	 * @return the value to bid on the lower market
	 */
	double divisor = 3.0;
	private double lowerMarketBidPrice(double ratio)
	{
		return Math.pow(ratio, .5);
	}
	
	/**
	 * TODO MAKE MACRO VARIABLES
	 * @param ratio the ratio of the higher market to lower market ratio
	 * @return the value to ask on the higher market
	 */
	private double higherMarketAskPrice(double ratio)
	{
		return ratio;
	}
	/**
	 * this function the amount of the returns the ratio of higher market ask price to lower market bid price
	 * @param robotBidPrice the robotBidPrice
	 * @param robotAskPrice the robotAskPrice
	 * @param snowBidPrice the snowBidPrice
	 * @param snowAskPrice the snow AskPrice
	 * @param b
	 * @return
	 */
	private double arbitrageRatio(double robotBidPrice, double robotAskPrice,
			double snowBidPrice, double snowAskPrice, boolean robotHigherMarket) {
		// TODO Auto-generated method stub
		if(robotHigherMarket)
		{
			return robotAskPrice / snowBidPrice;
		}
		return snowAskPrice / robotBidPrice;
	}

	public Quote[] refreshQuotes() {
		Quote[] quotes = new Quote[2];
		adjustPriceForLimit();
		quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
		quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
		return quotes;
	}

	/**
	 * this function adjusts the prices if it is above either limit to the limits
	 */
	int minimumPosition = 10;
	double NEAR_LIMIT_BID_OFFSET = .95;
	double NEAR_LIMIT_ASK_OFFSET = .97;
	double BELOW_MIN_ASK_OFFSET = 3;
	private void adjustPriceForLimit()
	{
		//If there is no ARBITRAGE hold back on the amount of stock to the NO_ARBITRAGE_LIMIT to keep liquidity to exploit ARBITRAGE
		if(position > ARBITRAGE_LIMIT && !(ROBOT_TO_SNOW_ARB || SNOW_TO_ROBOT_ARB) )
		{
			desiredRobotPrices[0] = desiredRobotPrices[0] * NEAR_LIMIT_BID_OFFSET;
			desiredRobotPrices[1] = desiredRobotPrices[1] * NEAR_LIMIT_ASK_OFFSET;
			desiredSnowPrices[0] = desiredSnowPrices[0] * NEAR_LIMIT_BID_OFFSET;
			desiredSnowPrices[1] = desiredSnowPrices[1] * NEAR_LIMIT_ASK_OFFSET;
		}
		else if(position > ARBITRAGE_LIMIT && (ROBOT_TO_SNOW_ARB || SNOW_TO_ROBOT_ARB) )
		{
			desiredRobotPrices[0] = desiredRobotPrices[0] * NEAR_LIMIT_BID_OFFSET;
			desiredRobotPrices[1] = desiredRobotPrices[1] * NEAR_LIMIT_ASK_OFFSET;
			desiredSnowPrices[0] = desiredSnowPrices[0] * NEAR_LIMIT_BID_OFFSET;
			desiredSnowPrices[1] = desiredSnowPrices[1] * NEAR_LIMIT_ASK_OFFSET;
		}
		if(position < minimumPosition)
		{
			desiredRobotPrices[1] = desiredRobotPrices[1] * BELOW_MIN_ASK_OFFSET;
			desiredSnowPrices[1] = desiredSnowPrices[1] * BELOW_MIN_ASK_OFFSET;
		}
	}

	public ArbCase getArbCaseImplementation() {
		return this;
	}

}
