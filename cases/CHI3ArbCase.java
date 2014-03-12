import java.util.ArrayList;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import java.lang.Math;

public class CHI3ArbCase extends AbstractExchangeArbCase implements ArbCase {
 
  
 // Note...the IDB will be used to save data to the hard drive and access it later
 // This will be useful for retrieving data between rounds
 private IDB myDatabase;
 int factor;
 double cashIN = 0;
 double cashOUT = 0;
 int position=0;
 
    ArrayList <Double> robotSpreadHistory = new ArrayList <Double> ();
    ArrayList <Double> robotMidpointHistory = new ArrayList <Double> ();
    ArrayList <Double> snowSpreadHistory = new ArrayList <Double> ();
    ArrayList <Double> snowMidpointHistory = new ArrayList <Double> ();
 

 public void addVariables(IJobSetup setup) {
  // Registers a variable with the system.
  setup.addVariable("someFactor", "factor used to adjust something", "int", "0");
  setup.addVariable("cashIN", "cash from selling", "double", "0");
  setup.addVariable("cashOUT", "cash to buying", "double", "0");
  setup.addVariable("position", "our current position", "int", "0");
  setup.addVariable("robotSpreadHistory", "spreads on robot", "ArrayList<Double>", "0");
  setup.addVariable("robotMidpointHistory", "midpoint on robot", "ArrayList<Double>", "0");
  setup.addVariable("snowSpreadHistory", "spreads on snow", "ArrayList<Double>", "0");
  setup.addVariable("snowMidpointHistory", "midpoint on snow", "ArrayList<Double>", "0");

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
   cashOUT += price;
  }else{
   position -= 1;
   cashIN += price;
  }
  double pANDl = cashIN - cashOUT + price*position;
  log("My P&L is:" + pANDl);
 }


 public void positionPenalty(int clearedQuantity, double price) {
  log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
  position -= clearedQuantity;
 }

 public void newTopOfBook(Quote[] quotes) {
  for (Quote quote : quotes) {
   log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
   if(quote.exchange == Exchange.ROBOT)
   {
    robotSpreadHistory.add(quote.askPrice - quote.bidPrice);
    robotMidpointHistory.add(.5*(quote.askPrice + quote.bidPrice));
   }
   else if(quote.exchange == Exchange.SNOW){
    snowSpreadHistory.add(quote.askPrice - quote.bidPrice);
    snowMidpointHistory.add(.5*(quote.askPrice + quote.bidPrice));
   }
  }
 }
 
 public Quote[] refreshQuotes() {
  Quote[] quotes = new Quote[2];
  int end = robotSpreadHistory.size()-1;

	  double robotBid = robotMidpointHistory.get(end) - .5*robotSpreadHistory.get(end);
	  double snowBid = snowMidpointHistory.get(end) - .5*snowSpreadHistory.get(end);
	  double robotAsk = 9999999;
	  double snowAsk = 9999999;
	  if(position>190)
	  	{
		robotBid = .01;
		snowBid = .01;
	  	}

  quotes[0] = new Quote(Exchange.ROBOT, robotBid, robotAsk);
  quotes[1] = new Quote(Exchange.SNOW, snowBid, snowAsk);
  return quotes;
 }
 


 public ArbCase getArbCaseImplementation() {
  return this;
 }

}