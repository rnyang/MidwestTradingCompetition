import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class HAR1ArbCaseImplementation extends AbstractExchangeArbCase implements ArbCase {
	
		
	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	private IDB myDatabase;
	int factor;
    double money = 0;
	int position = 0;
	double fair;
    double spread;
    double offset;
    double[] bidhistory1 = new double[3000];
    double[] askhistory1 = new double[3000];
    double[] bidhistory2 = new double[3000];
    double[] askhistory2 = new double[3000];
    double vol;
    int time = 0;
    double temp;
    double pnlest;
    double[] lagchanges = new double[3000];
    double abslagchange = 0;

    public double abs(double in) {
        if (in>0) {
            return in;
        }
        else {
            return -in;
        }
    }
    
    public double roundup(double initial) {
        double temp = initial * 4;
        return (((((double) ((int) temp))+1)/4)-0.0000001);
    }
    
    public double rounddown(double initial) {
        double temp = initial * 4;
        return (((((double) ((int) temp)))/4)+0.0000001);
    }
    
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
			position += 1;
            money -= price;
            log("current position:" + position);
            log("current cash:" + money);
		}else{
			position -= 1;
            money += price;
            log("current position:" + position);
            log("current cash:" + money);
		}
	}


	public void positionPenalty(int clearedQuantity, double price) {
		log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		position -= clearedQuantity;
        money += (clearedQuantity*price);
	}

	public void newTopOfBook(Quote[] quotes) {
		for (Quote quote : quotes) {
			log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
		}
        bidhistory1[time] = quotes[0].bidPrice;
        bidhistory2[time] = quotes[1].bidPrice;
        askhistory1[time] = quotes[0].askPrice;
        askhistory2[time] = quotes[1].askPrice;
        if (time>5) {
            lagchanges[(time-5)] = (askhistory1[time]+askhistory2[time]+bidhistory1[time]+bidhistory2[time])-(askhistory1[(time-5)]+askhistory2[(time-5)]+bidhistory1[(time-5)]+bidhistory2[(time-5)]);
            abslagchange += abs(lagchanges[(time-5)]);
        }
        ++time;
        pnlest = money + (position * (quotes[0].askPrice + quotes[0].bidPrice + quotes[1].askPrice + quotes[1].bidPrice) / 4);
        log("PnL Estimate:" + pnlest);
        if (time<11) {
            vol = ((quotes[0].askPrice - quotes[0].bidPrice + quotes[1].askPrice - quotes[1].bidPrice));
        }
        else {
            temp = 1/1000;
            vol = 0;
            for (int i=time-10;i<time;++i) {
                vol += (((askhistory1[i]+askhistory2[i]+bidhistory1[i]+bidhistory2[i]) - (askhistory1[i-1]+askhistory2[i-1]+bidhistory1[i-1]+bidhistory2[i-1])) * temp);
                temp *= 2;
            }
        }
        fair = ((quotes[0].bidPrice + quotes[0].askPrice + quotes[1].bidPrice + quotes[1].askPrice) / 4);
        spread = (((((quotes[0].askPrice - quotes[0].bidPrice + quotes[1].askPrice - quotes[1].bidPrice)))) + vol + (abslagchange / time)) * 0.9;
        offset = (((spread / 2) * (-position)) / 20.0);
	}


	public Quote[] refreshQuotes() {
		Quote[] quotes = new Quote[2];
        if (position > 10) {
            quotes[0] = new Quote(Exchange.ROBOT, rounddown(fair-spread+offset-100), roundup(fair+spread+offset));
            quotes[1] = new Quote(Exchange.SNOW, rounddown(fair-spread+offset-100), roundup(fair+spread+offset));
            log("quoting bid-ask:" + rounddown(fair-spread+offset-100) + " at " + roundup(fair+spread+offset));
            return quotes;
        }
        else if (position < -10) {
            quotes[0] = new Quote(Exchange.ROBOT, rounddown(fair-spread+offset), roundup(fair+spread+offset+100));
            quotes[1] = new Quote(Exchange.SNOW, rounddown(fair-spread+offset), roundup(fair+spread+offset+100));
            log("quoting bid-ask:" + rounddown(fair-spread+offset) + " at " + roundup(fair+spread+offset+100));
            return quotes;
        }
        else {
            quotes[0] = new Quote(Exchange.ROBOT, rounddown(fair-spread+offset), roundup(fair+spread+offset));
            quotes[1] = new Quote(Exchange.SNOW, rounddown(fair-spread+offset), roundup(fair+spread+offset));
            log("quoting bid-ask:" + rounddown(fair-spread+offset) + " at " + roundup(fair+spread+offset));
            return quotes;
        }
	}


	public ArbCase getArbCaseImplementation() {
		return this;
	}

}
