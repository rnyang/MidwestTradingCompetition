
import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

import java.util.*;
public class CMU2ArbSolution extends AbstractExchangeArbCase {
	
	class NarainArbImplementation implements ArbCase {
		// Note...the IDB will be used to save data to the hard drive and access it later
		// This will be useful for retrieving data between rounds
		private IDB myDatabase;
		int factor;
		
		//Round-specific Parameters
		double underlying_vol;
		double shock_std;
		double spread_mean;
		double spread_std;
		double askAggr;
		double bidAggr;
		double bidEF;
		double askEF;
		double aBF;
		double bBF;
		int limit;
		int numOfTicks;
		double askEFLimit;
		double bidEFLimit;
		int limitChange;
		
		//My parameters
		double AskBase;
		double BidBase;
		LinkedList<Double> mid1 = new LinkedList<Double>();
		LinkedList<Double> mid2 = new LinkedList<Double>();
		LinkedList<Double> oldSpreads1 = new LinkedList<Double>();
		LinkedList<Double> oldSpreads2 = new LinkedList<Double>();
		int time;
		
		//Older Variables
		double position;
		double ex1_Robot_Ask = 0;
		double ex1_Robot_Bid = 0;
		double ex2_Snow_Ask = 0;
		double ex2_Snow_Bid = 0;
		
		double[] ex1_Robot = new double[2];
		double[] ex2_Snow = new double[2];
		double PnL;
		
		public NarainArbImplementation(){
			PnL = 0;
			time = 4;
		}
		
		public void addVariables(IJobSetup setup) {
			
			// Registers a variable with the system.
			setup.addVariable("underlying_vol", "", "double", "1.5");
			setup.addVariable("shock_std", "", "double", "0.5");
			setup.addVariable("spread_mean", "", "double", "1.0");
			setup.addVariable("spread_std", "", "double", "0.3");
			setup.addVariable("askAggr","","double","0.0");
			setup.addVariable("bidAggr","","double","0.0");
			setup.addVariable("bidEF","","double","1.0");
			setup.addVariable("askEF","","double","1.0");
			setup.addVariable("bBF","","double","-.7");
			setup.addVariable("aBF","","double",".7");
			setup.addVariable("limit","","int","25");
			setup.addVariable("num of ticks","","int","5");
			setup.addVariable("bidEFLimit","","double","1.5");
			setup.addVariable("askEFLimit","","double","1.5");
			setup.addVariable("limitChange","","int","15");
		}
		
		public void initializeAlgo(IDB database) {
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			underlying_vol = getDoubleVar("underlying_vol");
			shock_std = getDoubleVar("shock_std");
			spread_mean = getDoubleVar("spread_mean");
			spread_std = getDoubleVar("spread_std");
			askAggr = getDoubleVar("askAggr");
			bidAggr = getDoubleVar("bidAggr");
			askEF = getDoubleVar("askEF");
			bidEF = getDoubleVar("bidEF");
			aBF = getDoubleVar("aBF");
			bBF = getDoubleVar("bBF");
			limit = getIntVar("limit");
			numOfTicks = getIntVar("num of ticks");
			askEFLimit = getDoubleVar("askEFLimit");
			bidEFLimit = getDoubleVar("bidEFLimit");
			limitChange = getIntVar("limitChange");
			
			log("underlying_vol="+underlying_vol+" shock_std=" + shock_std+" spread_mean="+spread_mean + " spread_std=" + spread_std);
			
			AskBase = (aBF*.9)*(underlying_vol+shock_std+spread_mean+spread_std);
			BidBase = (bBF*.9)*(underlying_vol+shock_std+spread_mean+spread_std);
			
			log ("t=0. AlgoInitialized");
			
		}
		
		@Override
		public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
			
			log ("t="+time+" " + algoside + " - " + exchange + " ,price=" + price);
			
			if(algoside == AlgoSide.ALGOBUY){
				position += 1;
				PnL -= price;
			}else{
				position -= 1;
				PnL += price;
			}
			log ("## t="+time + " PnL=$" + PnL + " position="+position);
			
		}
		
		@Override
		public void positionPenalty(int clearedQuantity, double price) {
			log("!! t="+time+"Position penalty with " + clearedQuantity + " positions cleared at " + price);
			position -= clearedQuantity;
		
			PnL += (clearedQuantity*price);
			log ("## t="+time + " PnL=$" + PnL + " position="+position);
		}
		
		@Override
		public void newTopOfBook(Quote[] quotes) {
			
			System.out.println(time);
			time+=1;
			
			for (Quote quote : quotes)
				log("QQ t="+time+" " +quote.exchange+": bid =" + quote.bidPrice + ", ask =" + quote.askPrice);
				
			//Updates all my parameters
			oldSpreads1.addLast(0.5*(quotes[0].askPrice-quotes[0].bidPrice));
			oldSpreads2.addLast(0.5*(quotes[1].askPrice-quotes[1].bidPrice));	
			mid1.addLast(0.5*(quotes[0].askPrice+quotes[0].bidPrice));
			mid2.addLast(0.5*(quotes[1].askPrice+quotes[1].bidPrice));
			
			if (time>= (5+numOfTicks)){
				mid1.removeFirst();
				mid2.removeFirst();
				if (time>=105){
					oldSpreads1.removeFirst();
					oldSpreads2.removeFirst();
				}
			}
			if (quotes[0].bidPrice > quotes[1].askPrice || quotes[1].bidPrice > quotes[0].askPrice){	
				log("$$ Pure abritrage discovered at t="+time+"!$$");
			}
			
			if (time==999){
				double clearedPosValue = 0;
				if (position<=0)
					clearedPosValue = Math.min(quotes[0].askPrice, quotes[1].askPrice)*((double)position);
				else
					clearedPosValue = Math.max(quotes[0].bidPrice, quotes[1].bidPrice)*((double)position);
				
				log("@@@@ End of Round. Total PnL=" + (PnL+clearedPosValue) + " @@@@");				
			}
		
		}
		
		@Override
		public Quote[] refreshQuotes() {
			//Compute RecentBidSpreads and RecentAskSpreads
			LinkedList<Double> recentSpreads1 = new LinkedList<Double>();
			LinkedList<Double> recentSpreads2 = new LinkedList<Double>();
			
			if (time >6){
				
				for (int i=oldSpreads1.size()-numOfTicks; i<oldSpreads1.size(); i++){
					recentSpreads1.addLast(oldSpreads1.get(i));
					recentSpreads2.addLast(oldSpreads2.get(i));
				}
					
				//Compute the avg bids, asks, and mids
				double bid1 = avgBidAsk(recentSpreads1, mid1,-1.0);
				double ask1 = avgBidAsk(recentSpreads1, mid1, 1.0);
				double bid2 = avgBidAsk(recentSpreads2, mid2,-1.0);
				double ask2 = avgBidAsk(recentSpreads2, mid2, 1.0);
				double mid1Avg = mean(mid1);
				double mid2Avg = mean(mid2);
				
				if (bid2>ask1 || bid1>ask2){
					log("AA Pure aribitrage discovered!!!");
					
					ex1_Robot_Ask = 0.5*(ask1+ask2);
					ex2_Snow_Ask = 0.5*(ask1+ask2);
					ex1_Robot_Bid = 0.5*(bid1+bid2);
					ex2_Snow_Ask = 0.5*(bid1+bid2);
				} else {
				
					//Compute the aggressive factors
					double Ex1BidAggr = bidAggr*(bid2-bid1);
					double Ex1AskAggr = askAggr*(ask2-ask1);
					double Ex2BidAggr = bidAggr*(bid1-bid2);
					double Ex2AskAggr = askAggr*(ask1-ask2);
					double s1Factor = (mean(oldSpreads1)!=0 && mean(recentSpreads1)!=0) ? Math.sqrt(mean(recentSpreads1)/mean(oldSpreads1)) : 1;
					double s2Factor = (mean(oldSpreads2)!=0 && mean(recentSpreads2)!=0) ? Math.sqrt(mean(recentSpreads2)/mean(oldSpreads2)) : 1;
					
					log("FF t="+time + "  s1Factor=" + s1Factor + " s2Factor=" + s2Factor);
					
					if (position <= -limit){
						askAggr = 0;
						askEF = askEFLimit;
						
						log("LL Sell Limit Hit, L="+limit);
						
						if (PnL+ ( position>=0 ? position*Math.max(mid1.getLast(), mid2.getLast()) : position*Math.min(mid1.getLast(), mid2.getLast())) <0 ){
							limit+= limitChange;
						}
					} else {
						askAggr = getDoubleVar("askAggr");
						askEF = getDoubleVar("askEF");
					}
					
					if (position >= limit){
						bidAggr = 0;
						bidEF = bidEFLimit;
						
						log("LL Buy Llimit Hit, L="+limit);
						
						if (PnL+ ( position>=0 ? position*Math.max(mid1.getLast(), mid2.getLast()) : position*Math.min(mid1.getLast(), mid2.getLast())) <0 ){
							limit += limitChange;
						}
					} else {
						bidAggr = getDoubleVar("bidAggr");
						bidEF = getDoubleVar("bidEF");
					}
					
					
					ex1_Robot_Ask = mid1Avg + (AskBase*askEF*s1Factor)+Ex1AskAggr;
					ex1_Robot_Bid = mid1Avg + (BidBase*bidEF*s1Factor)+Ex1BidAggr;
					ex2_Snow_Ask = mid2Avg + (AskBase*askEF*s2Factor)+Ex2AskAggr;
					ex2_Snow_Bid = mid2Avg +  (BidBase*bidEF*s2Factor)+Ex2BidAggr;
					
				}
			}	else {
			
				ex1_Robot_Ask = 100.5; 
				ex1_Robot_Bid = 99.5;
				ex2_Snow_Ask = 100.5;
				ex2_Snow_Bid = 99.5;
			}
			
			Quote[] quotes = new Quote[2];
			log("NN t="+time+" Robot: bid="+ex1_Robot_Bid + ", ask=" + ex1_Robot_Ask);
			log("NN t="+time+" Snow: bid="+ex2_Snow_Bid+", ask="+ex2_Snow_Ask);
			
			quotes[0] = new Quote(Exchange.ROBOT, ex1_Robot_Bid, ex1_Robot_Ask);
			quotes[1] = new Quote(Exchange.SNOW, ex2_Snow_Bid, ex2_Snow_Ask);
			return quotes;
		}
		
		//BidAskFactor equals 1 for asks and -1 for bids.
		private double avgBidAsk(LinkedList<Double> recentSpreads, LinkedList<Double> mids, double bidAskFactor){
			
			double toReturn = 0;
			for (int i=0; i<mids.size(); i++){
				toReturn += (mids.get(i)+(bidAskFactor*recentSpreads.get(i)));
			}
			
			return toReturn/((double) mids.size());
		}

		private double mean(LinkedList<Double> l){
			double toReturn = 0;
			for (Double d: l){
				toReturn +=d;
			}
			return toReturn/l.size();
		}
		
		private double std(LinkedList<Double> l){
			double toReturn = 0;
			double mean = mean(l);
			
			for (Double d: l)
				toReturn += Math.pow(d-mean, 2);
			
			return Math.sqrt(toReturn/l.size());
		}
	}

	@Override
	public ArbCase getArbCaseImplementation() {
		return new NarainArbImplementation();
	}
	
	
	public static void main (String [] args){
	
		/*NarainArbSolution n = new NarainArbSolution();
		ArbCase a = n.getArbCaseImplementation();
		
		Quote [] q = new Quote[2];
		q[0] = new Quote(Exchange.ROBOT,99,101);
		q[1] = new Quote(Exchange.SNOW,99.5,100.5);
		
		for (int i=0; i<5; i++){
			a.newTopOfBook(q);
			
		}
		*/
		
		
	}
}
