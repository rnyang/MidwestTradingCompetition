import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;


public class CHI2ArbCase extends AbstractExchangeArbCase {
	
	class MySampleArbImplementation implements ArbCase {
		

	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;
	int factor;

    int t = 0;
    int strategy;
    double k;
    double a;
    double threshold;
    double r;
    double q;
    double l;
	int position;
    double pnl = 0;
	double[] desiredRobotPrices = new double[2];
	double[] desiredSnowPrices = new double[2];


		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
            setup.addVariable("r", "r for case 1, spread", "double", "1.899");
            setup.addVariable("q", "q for case 1, denominator", "double", "27");
            setup.addVariable("strategy", "1 for Round 1, 2 for Round 2, 3 and 4 for Round 3", "int", "1");
            setup.addVariable("k", "k for cases 3 and 4", "double", "0.57");
            setup.addVariable("a", "a for cases 3 and 4", "double", "2.5");
            setup.addVariable("l", "l for cases 3 and 4", "double", "0");
            setup.addVariable("threshold", "threshold for cases 3 and 4", "double", "0.5");
		}

		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			database.put("currentPosition", 10);

            strategy = getIntVar("strategy");
            k = getDoubleVar("k");
            a = getDoubleVar("a");
            threshold = getDoubleVar("threshold");
            r = getDoubleVar("r");
            q = getDoubleVar("q");
            l = getDoubleVar("l");

		}

		@Override
		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside + " at " + t);
			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
                pnl -= price;
			}else{
				position -= 1;
                pnl += price;
			}

            log("My position is now " + position + " and my PnL is "+ pnl);
		}

		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		}

		@Override
		public void newTopOfBook(Quote[] quotes) {
            log("You are using a strategy of: " + strategy);

			for (Quote quote : quotes) {
				log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange
                    + " from " + (t) );
			}
            t++;

            double mmpoint = midmidpoint(quotes);
            double robotMid = midpoint(quotes[0]);
            double snowMid = midpoint(quotes[1]);

            //round 1: controlled carry risk
            if (strategy == 1)
            {
                desiredRobotPrices[0] = mmpoint - r - Math.copySign(Math.pow((position / q),2), position);
                desiredRobotPrices[1] = mmpoint + r - Math.copySign(Math.pow((position / q),2), position);
                desiredSnowPrices[0] = mmpoint - r - Math.copySign(Math.pow((position / q),2), position);
                desiredSnowPrices[1] = mmpoint + r - Math.copySign(Math.pow((position / q),2), position);
            }

            //round 2: sitting it out
            if (strategy == 2)
            {
                desiredRobotPrices[0] = 0; //BUY
                desiredRobotPrices[1] = 500;   //SELL
                desiredSnowPrices[0] = 0; //BUY
                desiredSnowPrices[1] = 500;     //SELL
            }

            //round 3 with both arbitrage and market making
            if (strategy == 3)
            {
                log("You are using k of " + k + " a of " + a + " and a threshold of " + threshold);
                if((Math.max(quotes[0].bidPrice,quotes[1].bidPrice) - Math.min(quotes[0].askPrice,quotes[1].askPrice)) > threshold){

                    if(quotes[0].bidPrice > quotes[1].askPrice){
                        desiredRobotPrices[0] = quotes[0].bidPrice -a*k;
                        desiredRobotPrices[1] = quotes[0].bidPrice + l;
                        desiredSnowPrices[1] = quotes[1].askPrice +a*k;
                        desiredSnowPrices[0] = quotes[1].askPrice - l;
                    }
                    if(quotes[1].bidPrice > quotes[0].askPrice){
                        desiredRobotPrices[0] = quotes[0].askPrice - l;
                        desiredRobotPrices[1] = quotes[0].askPrice +a*k;
                        desiredSnowPrices[0] = quotes[1].bidPrice -a*k;
                        desiredSnowPrices[1] = quotes[1].bidPrice + l;
                    }
                }

                else {
                    desiredRobotPrices[0] = mmpoint - 6.5;
                    desiredRobotPrices[1] = mmpoint + 6.5;
                    desiredSnowPrices[0] = mmpoint - 6.5;
                    desiredSnowPrices[1]= mmpoint + 6.5;
                }
            }

            //round 3 with only arbitrage
            if (strategy == 4)
            {
                if((Math.max(quotes[0].bidPrice,quotes[1].bidPrice) - Math.min(quotes[0].askPrice,quotes[1].askPrice)) > threshold){
                    log("You are using k of " + k + " a of " + a + " and a threshold of " + threshold);

                    if(quotes[0].bidPrice > quotes[1].askPrice){
                        desiredRobotPrices[0] = quotes[0].bidPrice -a*k;
                        desiredRobotPrices[1] = quotes[0].bidPrice + l;
                        desiredSnowPrices[1] = quotes[1].askPrice +a*k;
                        desiredSnowPrices[0] = quotes[1].askPrice - l;
                    }
                    if(quotes[1].bidPrice > quotes[0].askPrice){
                        desiredRobotPrices[0] = quotes[0].askPrice - l;
                        desiredRobotPrices[1] = quotes[0].askPrice +a*k;
                        desiredSnowPrices[0] = quotes[1].bidPrice -a*k;
                        desiredSnowPrices[1] = quotes[1].bidPrice + l;
                    }
                }
            }

            //just the built in strategy
            if (strategy == 5){
                desiredRobotPrices[0] = quotes[0].bidPrice + 0.2;
                desiredRobotPrices[1] = quotes[0].askPrice - 0.2;

                desiredSnowPrices[0] = quotes[1].bidPrice + 0.2;
                desiredSnowPrices[1] = quotes[1].askPrice - 0.2;
            }

            //for fun
            if (strategy == 6){
                desiredRobotPrices[0] = 99.7;
                desiredRobotPrices[1] = 100.3;

                desiredSnowPrices[0] = 99.7;
                desiredSnowPrices[1] = 100.3;
            }



            if (t >= 95)
            {
                if (position > 0 )
                {
                    if (quotes[0].bidPrice > quotes[1].bidPrice)
                    {
                        pnl += (quotes[0].bidPrice * position);
                    }
                    else
                    {
                        pnl += (quotes[1].bidPrice * position);
                    }
                }
                if (position < 0 )
                {
                    if (quotes[0].askPrice > quotes[1].askPrice)
                    {
                        pnl -= (quotes[0].askPrice * position);
                    }
                    else
                    {
                        pnl -= (quotes[1].askPrice * position);
                    }
                }
                log("Your final profits are " + pnl);
            }
		}

		@Override
		public Quote[] refreshQuotes() {
			Quote[] quotes = new Quote[2];
			quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
			quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
            log("My quotes are " + "a bid of "+ quotes[0].bidPrice + " and an ask of " + quotes[0].askPrice +" on ROBOT" +
                    " and are" + " a bid of "+ quotes[1].bidPrice +  " and an ask of " + quotes[1].askPrice + " on SNOW "
            + "at a time of " + (t+4));
			return quotes;
		}

        //calculates the midmidpoint, approximating for true value
        public double midmidpoint (Quote[] quotes){
            double midpoint0 = (quotes[0].bidPrice + quotes[0].askPrice) /2;
            double midpoint1 = (quotes[1].bidPrice + quotes[1].askPrice) /2;
            return (midpoint0 + midpoint1)/2;
        }

        //calculates midpoint
        public double midpoint (Quote quote){
            return (quote.bidPrice + quote.askPrice) /2;
        }
    }


	@Override
	public ArbCase getArbCaseImplementation() {
		return new MySampleArbImplementation();
	}

}
