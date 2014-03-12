import java.util.ArrayList;
import org.chicago.cases.AbstractMathCase;

import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a barebones sample of a MathCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class CHI3MathCase extends AbstractMathCase implements MathCase {
	
		
		private IDB myDatabase;
        int contract = 0;
        ArrayList <Integer> bidHistory = new ArrayList <Integer> ();
        ArrayList <Integer> askHistory = new ArrayList <Integer> ();
        ArrayList <Integer> e1History = new ArrayList <Integer> ();
        ArrayList <Integer> e2History = new ArrayList <Integer> ();
        double cashOUT = 0;
        double cashIN = 0;

        int [][] e1Count = new int [9][9];//row is observation in t-1, column is observation in time t
        int [][] e2Count = new int [3][3];//row is observation in t-1, column is observation in time t


		public void addVariables(IJobSetup setup) {
			// Registers a variable with the system.
			setup.addVariable("someFactor", "factor used to adjust something", "int", "0");
			setup.addVariable("contract", "contracts we own", "int", "0");
			setup.addVariable("bidHistory", "history of bids", "Integer Arraylist", "0");
			setup.addVariable("askHistory", "history of asks", "Integer Arraylist", "0");
			setup.addVariable("e1History", "history of e1", "Integer Arraylist", "0");
			setup.addVariable("e2History", "history of e2", "Integer Arraylist", "0");
			setup.addVariable("e1Count", "e1 counts of outcomes", "Integer Array", "0");
			setup.addVariable("e2Count", "e2 counts of outcomes", "Integer Array", "0");
			setup.addVariable("contract", "our current contract", "int", "0");
			setup.addVariable("cashOUT", "cash spent on ctx", "double", "0");
			setup.addVariable("cashIN", "cash recieved from selling ctx", "double", "0");
			
		}

		public void initializeAlgo(IDB database) {
			
			// Databases can be used to store data between rounds
			myDatabase = database;
			
			// helper method for accessing declared variables
			//factor = getIntVar("someFactor"); 
		}

		public int newBidAsk(double bid, double ask) {
			log("I received a new bid of " + bid + ", and ask of " + ask);
			recordPriceData((int) bid,(int)ask);//records our new data
			int endindex = e2History.size()-1;//most recent spread
			
            //for the first n ticks, we are long 5 ctx
			if(bidHistory.size()<100)//n = 100
			{
				if(contract ==0 && e2History.get(endindex)==1)//if we dont have our first 5 get them
				{
					//contract +=5;//adjust contract
					return 5;
				}
			}
			
			return endRoundStrategy();
		}
		
		public int endRoundStrategy()
		{
			int endindex = e1History.size()-1;
			if(e1History.get(endindex) != 1)
			{
				return 0;
			}
			int myE1 = e1History.get(endindex);
			int myHash = hashE1(myE1);
			int countdown = e1Count[myHash][0] + e1Count[myHash][1] + e1Count[myHash][2];//how many traded down
			int countup = e1Count[myHash][6] + e1Count[myHash][7] + e1Count[myHash][8];//how many traded down
			
			if((countup+countdown)>6)//6 is an arbitrary threshold for sample size
			{
				if(isBullMarket())
				{
					if(countup>(countdown*1.5)&& contract !=5)
					{
						return (5-contract);
					}
				}
				else
				{
					if(countdown<(countup*1.5)&& contract != -5)
					{
						return (-5-contract);
					}
				}
			}

				return 0;

		}
		//returns true if bull and false if bear
		public boolean isBullMarket()
		{
			int endindex = askHistory.size()-1;
			
			//assume bull if we dont have data
			if(askHistory.size()>100)
			{
				return true;
			}
			//if we are lower than we were 50 and 25 ago, assume bear market
			else if((askHistory.get(endindex-50)>askHistory.get(endindex)) &&(askHistory.get(endindex-25)>askHistory.get(endindex)))
			{
				return false;
			}
			else
			{
				return true;
			}
		}
		
		public void recordPriceData(int bid, int ask)
		{
			bidHistory.add(bid);
			askHistory.add(ask);
			e1History.add(getE1());
			e2History.add(getE2());
		}
		
		public void updateE1Count()
		{
			int endindex = e1History.size()-1;
			if(endindex<=0)
			{
				return;
			}
			else{
				int oldE1 = e1History.get(endindex-1);
				int newE1 = e1History.get(endindex);
				e1Count[hashE1(oldE1)][hashE1(newE1)] += 1;
			}
		}
		
		public void updateE2Count()
		{
			int endindex = e2History.size()-1;
			if(endindex<=0)
			{
				return;
			}
			else{
				int oldE2 = e2History.get(endindex-1);
				int newE2 = e2History.get(endindex);
				e1Count[hashE1(oldE2)][hashE1(newE2)] += 1;
			}
		}
		
		public int getE1()
		{
			if(bidHistory.size()>1)
			{
			int endindex = bidHistory.size()-1;//index of the most recent addition to the arraylist
			int new_midpoint = (askHistory.get(endindex)+bidHistory.get(endindex))/2;
			int old_midpoint = (askHistory.get(endindex-1)+bidHistory.get(endindex-1))/2;
			return (new_midpoint - old_midpoint);
			}
			else
			{
				return 0;
			}
		}
		
		//takes public arraylists and gets parameter e2 at most recent time period
		public int getE2()
		{
			int endindex = bidHistory.size()-1;//index of the most recent addition to the arraylist
			return (askHistory.get(endindex)-bidHistory.get(endindex))/2;
			//this is the difference between the midpoint and either/bid or ask
		}
		
		//given an observed value of E1, we get back an index in the count array
		public int hashE1(int E1)
		{
			int arrayIndex=0;
			
			switch(E1)	{
			case -10:	arrayIndex = 0;
						break;
			case -5:	arrayIndex = 1;
						break;
			case -3:	arrayIndex = 2;
						break;
			case -1:	arrayIndex = 3;
						break;
			case 0:		arrayIndex = 4;
						break;
			case 1:		arrayIndex = 5;
						break;
			case 3:		arrayIndex = 6;
						break;
			case 5:		arrayIndex = 7;
						break;
			case 10:	arrayIndex = 8;
						break;
			}
			
			return arrayIndex;
		}
		
		public int hashE2(int E2)
		{
			int arrayIndex=0;
			switch(E2)	{
			case 1:		arrayIndex = 0;
						break;
			case 3:		arrayIndex = 1;
						break;
			case 5:		arrayIndex = 2;
						break;
			}
			return arrayIndex;
		}

		// For your own benefit, keep track of your own PnL too
		public void orderFilled(int volume, double fillPrice) {
			int endindex = bidHistory.size()-1;
			log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
            // Keep track of your own contracts for your own benefit
            // Your contract should not exceed 5 net
            contract = contract + volume;
            if(volume>0)
            {
            cashOUT += volume*fillPrice;
            }
            if(volume<0)
            {
            	cashIN += volume*fillPrice*-1;
            }
            double lastMidpoint = .5*(askHistory.get(endindex)+bidHistory.get(endindex));
            double pANDl = cashIN - cashOUT + contract*lastMidpoint;
            log("My current position is: " + contract + " and P&L is " + pANDl);

		}


	public MathCase getMathCaseImplementation() {
		return this;
	}

}
