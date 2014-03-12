
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;
import com.optionscity.freeway.api.messages.MarketLastMessage;




import java.util.ArrayList;

import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.AbstractExchangeArbCase.ArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;
import com.optionscity.freeway.api.messages.MarketLastMessage;


public class BAR1Arb extends AbstractExchangeArbCase implements ArbCase {

	// Note...the IDB will be used to save data to the hard drive and access it later
	// This will be useful for retrieving data between rounds
	static Strategy asd=new Strategy();
	
	private IDB myDatabase;

	static int position, factor, check=0, flag=0;
	
	static double correlation[][],
		   		  desiredSnowPrices[] =new double[2],
		   		  desiredRobotPrices[]=new double[2],
		   		  prevtrend=0,
		   		  lastAsk=1000,
		   		  lastBid=0,
		   		  midup2=0,
		   		  midup1=0,
		   		  mid30=0;
	
	static double[] /*valuees=new double[20],
					values =new double[20],
					deve1s =new double[20],
					deve2s =new double[20],
					weight =new double[9 ],
					dev1s  =new double[20],
					dev2s  =new double[20],*/
					ex1s   =new double[10],
					ex2s   =new double[10],
					lm2    =new double[11],
					lm     =new double[11];
	
	static final int T = 1000;
	static int myRound;
	public static Random r = new Random();
	public static double underlying_vol,
		    	  		 spread_mean,
		    	  		 spread_std,
		    	  		 shock_std,
		    	  		 ex_mem,
		    	  		 p1;
	
	static ArrayList<double[]>	 RobotPrices=new ArrayList<double[]>(0),
								 SnowPrices =new ArrayList<double[]>(0),
								 positions  =new ArrayList<double[]>(0),
								 orders     =new ArrayList<double[]>(0),
								 a_m30a     =new ArrayList<double[]>(0);
	
	static ArrayList<double[][]> mavgs      =new ArrayList<double[][]>(0),
								 mean15     =new ArrayList<double[][]>(0),
								 prices		=new ArrayList<double[][]>(0),
								 spread     =new ArrayList<double[][]>(0),
								 SampleStd  =new ArrayList<double[][]>(0),
								 MarketPrice=new ArrayList<double[][]>(0);
								 
	public void addVariables(IJobSetup setup) {
		// Registers a variable with the system.
		setup.addVariable("myRound", "round", "int", "1");
	}

	public void initializeAlgo(IDB database) {
		if (myRound == 1){
			//Round 1
		    underlying_vol=0.3;
		    spread_mean=1.0;
		    spread_std=0.5;
		    shock_std=0.5;
		    ex_mem=0.1;
		    p1=0.5;
		}
		else if(myRound==2){
			// Round 2
			underlying_vol = 1.0;
			spread_mean = 2.0;
			spread_std = 0.5;
			shock_std = 0.5;
			ex_mem = 0.1;
			p1 = 0.5;
		}
		else if(myRound == 3){
			// Round 3
			underlying_vol = 0.2;
			spread_mean = 0.2;
			spread_std = 0.2;
			shock_std = 0.2;
			ex_mem = 0.225;
			p1 = 0.5;
		}
		else if(myRound == 4){
			// Round 3
			underlying_vol = 1.5;
			spread_mean = 1.0;
			spread_std = 0.3;
			shock_std = 0.5;
			ex_mem = 0.1;
			p1 = 0.5;
		}
		
		// Databases can be used to store data between rounds
		myDatabase=database;
		
		container.addProbe("_position", "The current bid", true); container.addProbe("_time", "The current bid", true); 
		
		container.addProbe("_MA1  R", "The current bid", true);  container.addProbe("_MA2  R", "The current ask", true);
		container.addProbe("_MA3  R", "The current bid", true);  container.addProbe("_MA4  R", "The current ask", true);
		container.addProbe("_MA5  R", "The current bid", true);  /*container.addProbe("_MA6  R", "The current ask", true);
		container.addProbe("_MA7  R", "The current bid", true);  container.addProbe("_MA8  R", "The current ask", true);
		container.addProbe("_MA9  R", "The current bid", true);  container.addProbe("_MA10 R", "The current ask", true);
		
		container.addProbe("_MA1 S", "The current bid", true);   container.addProbe("_MA2 S", "The current ask", true);
		container.addProbe("_MA3 S", "The current bid", true);   container.addProbe("_MA4 S", "The current ask", true);
		container.addProbe("_MA5 S", "The current bid", true);   container.addProbe("_MA6 S", "The current ask", true);
		*/
		container.addProbe("_bidR", "The current bid", true);    container.addProbe("_askR", "The current bid", true);
		
		container.addProbe("_bidS", "The current bid", true);    container.addProbe("_askS", "The current bid", true);
		
		container.addProbe("_mpr", "The current bid", true);     container.addProbe("_mps", "The current bid", true);
		container.addProbe("_mprg5", "The current bid", true);    container.addProbe("_mpsg5", "The current bid", true);
		container.addProbe("_mprg10", "The current bid", true);    container.addProbe("_mpsg10", "The current bid", true);
		
		container.addProbe("_qb", "The current bid", true);     container.addProbe("_qa", "The current bid", true);
		
		container.addProbe("_vol", "The current bid", true);    container.addProbe("_mid1", "The current bid", true);
		
		container.addProbe("_p", "The current bid", true);      container.addProbe("_mid2", "The current bid", true);
		
		// helper method for accessing declared variables
		factor = getIntVar("myRound"); 
		myRound=factor;
	}

	public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
		log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);
		
		if(algoside==AlgoSide.ALGOBUY) {
			position+=1; orders.add(new double[]{1,price});
		}
		else{
			position-=1; orders.add(new double[]{-1,price});
		}
	}

	public void positionPenalty(int clearedQuantity, double price) {
		//log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
		position -= clearedQuantity;
	}

	public void newTopOfBook(Quote[] quotes) {
		for (Quote quote : quotes) {
			//log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
		}
		
		RobotPrices.add(new double[]{quotes[0].bidPrice, quotes[0].askPrice,
									 (quotes[0].bidPrice+quotes[0].askPrice)/2,
									 (quotes[0].bidPrice-quotes[0].askPrice)/2});
		
		SnowPrices.add(new double[]{quotes[1].bidPrice, quotes[1].askPrice,
									(quotes[1].bidPrice+quotes[1].askPrice)/2,
									(quotes[1].bidPrice-quotes[1].askPrice)/2});
		
		prices.add(new double[][]{{quotes[0].bidPrice,quotes[0].askPrice},
							      {quotes[1].bidPrice,quotes[1].askPrice}});
		
		//container.getProbe("_position").set(position);        container.getProbe("_p").set(PnL());
		
		if(check==0){
			desiredRobotPrices[0]=-102; 
			desiredRobotPrices[1]=1002; 
			desiredSnowPrices[0]=-102;  
			desiredSnowPrices[1]=1002;
		}
		
		if(prices.size()>=11 && prices.size()%4==0){
			for(int i=0; i<11; i++){
				lm[i]=RobotPrices.get(RobotPrices.size()-i-1)[2];
				lm2[i]=SnowPrices.get(RobotPrices.size()-i-1)[2];
			}
			
			aprt();
			container.getProbe("_mprg5").set((/*ex1s[0]+ex1s[1]+ex1s[2]+ex1s[3]+*/ex1s[4])/1 /*5*/);
			container.getProbe("_mpsg5").set((/*ex2s[0]+ex2s[1]+ex2s[2]+ex2s[3]+*/ex2s[4])/1 /*5*/);
			container.getProbe("_mprg10").set((/*ex1s[5]+ex1s[6]+ex1s[7]+ex1s[8]+*/ex1s[9])/1 /*5*/);
			container.getProbe("_mpsg10").set((/*ex2s[5]+ex2s[6]+ex2s[7]+ex2s[8]+*/ex2s[9])/1 /*5*/);
			
			container.getProbe("_mps").set((RobotPrices.get(RobotPrices.size()-1)[0]+
											RobotPrices.get(RobotPrices.size()-1)[1])/2);
			container.getProbe("_mpr").set((SnowPrices.get(SnowPrices.size()-1)[0]+
											SnowPrices.get(SnowPrices.size()-1)[1])/2);	/**/
			
			/*
			container.getProbe("_mprg5_a").set(prices.size());
			container.getProbe("_mpsg5_a").set(prices.size());
			container.getProbe("_mprg10_a").set(prices.size());
			container.getProbe("_mpsg10_a").set(prices.size());*/
		}
		
		updateAll();	container.getProbe("_time").set(prices.size());
	}
	
	public void order(double bidR, double bidS, double askR, double askS){
		//if(flag==0){
		
		if((bidR+bidS+askR+askS)/4<(midup2+midup1)/2-3*underlying_vol){
			if(asd.robotTrend()<0){//long
				//(ex1s[4]+ex2s[4])/2-.2; (ex1s[4]+ex2s[4])/2+.2; 
				
				desiredRobotPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),
												 1000};//(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)+.2};
				desiredSnowPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),
											    1000};//(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)+.2};
			}
			
			else if(asd.robotTrend()>0){// && (bidR+bidS+askR+askS)/4>(ex1s[4]+ex2s[4])/2){//short
				desiredRobotPrices=new double[]{0,(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
				desiredSnowPrices=new double[]{0,(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
			}
		}
		
		else if((bidR+bidS+askR+askS)/4>(midup2+midup1)/2+3*underlying_vol){
			if(asd.robotTrend()>0){//long
				//(ex1s[4]+ex2s[4])/2-.2; (ex1s[4]+ex2s[4])/2+.2; 
				
				desiredRobotPrices=new double[]{0,(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
				desiredSnowPrices=new double[]{0,(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
			}
			
			else if(asd.robotTrend()<0){//&& (bidR+bidS+askR+askS)/4>(ex1s[4]+ex2s[4])/2){//short
				desiredRobotPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),1000};
				desiredSnowPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),1000};
			}
		}
		
		if((bidR+bidS+askR+askS)/4>(midup2+midup1)/2-3*underlying_vol &&
		   (bidR+bidS+askR+askS)/4<(midup2+midup1)/2+3*underlying_vol){
			//if(asd.robotTrend()>0){//long
				//(ex1s[4]+ex2s[4])/2-.2; (ex1s[4]+ex2s[4])/2+.2; 
			if(position>0){
				desiredRobotPrices=new double[]{0,
												(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
				desiredSnowPrices=new double[]{0,
						(bidR+bidS+askR+askS)/4+Math.pow(underlying_vol,underlying_vol)};
			}
			else{
				desiredRobotPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),
												1000};
				desiredSnowPrices=new double[]{(bidR+bidS+askR+askS)/4-Math.pow(underlying_vol,underlying_vol),
											   1000};
			}
			//}
				/*
			
			else if(asd.robotTrend()<0){//short
				desiredRobotPrices=new double[]{0,(ex1s[4]+ex2s[4])/2+Math.pow(underlying_vol,underlying_vol)};
				desiredSnowPrices=new double[]{0,(ex1s[4]+ex2s[4])/2+Math.pow(underlying_vol,underlying_vol)};
			}*/
		}
		
		/*else{
			if(position>0){
				desiredRobotPrices=new double[]{0,(ex1s[4]+ex2s[4])/2+Math.pow(underlying_vol,underlying_vol)};
				desiredSnowPrices=new double[]{0,(ex1s[4]+ex2s[4])/2+Math.pow(underlying_vol,underlying_vol)};
			}
			
			if(position<0){
				desiredRobotPrices=new double[]{(ex1s[4]+ex2s[4])/2-Math.pow(underlying_vol,underlying_vol),1000};
				desiredSnowPrices=new double[]{(ex1s[4]+ex2s[4])/2-Math.pow(underlying_vol,underlying_vol),1000};
			}	
		}*/
		//}/**/
		/*else{
			if(positions.size()>=2){
				if(position>0 && (bidR+bidS+askR+askS)/4>(ex1s[4]+ex2s[4])/2){//short
					//(ex1s[4]+ex2s[4])/2-.2; (ex1s[4]+ex2s[4])/2+.2; 
					
					desiredRobotPrices=new double[]{0,(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)};
					desiredSnowPrices=new double[]{0,(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)};
					
					check=1;
					flag=0;
					
					prevtrend=asd.robotTrend();
					lastBid=(ex1s[4]+ex2s[4])/2-.2;		
					lastAsk=(ex1s[4]+ex2s[4])/2+underlying_vol;
				}
				
				else if(position<0 && (bidR+bidS+askR+askS)/4>(ex1s[4]+ex2s[4])/2){//long
					//(ex1s[4]+ex2s[4])/2-.2; (ex1s[4]+ex2s[4])/2+.2; 
					
					desiredRobotPrices=new double[]{0,//(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol),
													 (ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)+.2};
					desiredSnowPrices=new double[]{0,//(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol),
						    						(ex1s[4]+ex2s[4])/2-Math.sqrt(underlying_vol)+.2};
					
					check=1;
					flag=0;
					prevtrend=asd.robotTrend();
					lastBid=(ex1s[4]+ex2s[4])/2-.2;		
					lastAsk=(ex1s[4]+ex2s[4])/2+underlying_vol;
				}
			}
			
			else if((flag==1 && position>0 && asd.robotTrend()>0) || 
					(flag==-1 && position<0 && asd.robotTrend()<=0)){
				flag=0;
			}
		}/**/
		//if(position==0){lastBid=0; lastAsk=1000;}
		
		/*else{
			desiredRobotPrices[0]=-102; desiredRobotPrices[1]=1002; desiredSnowPrices[0]=-102;  desiredSnowPrices[1]=1002;
		}*/
	}
	
	
	public void updateAll(){
		if(midup2==0)
			midup2=(RobotPrices.get(RobotPrices.size()-1)[2]+
					SnowPrices.get(SnowPrices.size()-1)[2])/2;
		else
			midup2=(midup2*(prices.size()-1)+
				   (RobotPrices.get(RobotPrices.size()-1)[2]+
					SnowPrices.get(SnowPrices.size()-1)[2])/2)/prices.size();
		
		container.getProbe("_mid2").set(midup2);  asd.setPrice(prices);
		
		if(RobotPrices.size()>=5){asd.UpateSampleVol(); SampleStd=asd.SampleStd;}
		
		if(prices.size()>=15){
			if(prices.size()==15) {asd.InitiateMavgs(); mavgs=asd.mavgs;}
			else{
				asd.UpdateMavgs(); mavgs=asd.mavgs;
				
				if(prices.size()>=20){
					if(prices.size()==20)
						for(int i=0; i<20; i++)
							midup1+=((RobotPrices.get(RobotPrices.size()-i-1)[2]+
									  SnowPrices.get(SnowPrices.size()-i-1)[2])/2)/20;
					
					else
						midup1=midup1-((RobotPrices.get(RobotPrices.size()-20)[2]+
								        SnowPrices.get(SnowPrices.size()-20)[2])/2)/20+
								      ((RobotPrices.get(RobotPrices.size()-1)[2]+
									    SnowPrices.get(SnowPrices.size()-1)[2])/2)/20;
					
					a_m30a.add(new double[]{midup1}); 
					mid30=(mid30*(a_m30a.size()-1)+midup1)/a_m30a.size();
					
					container.getProbe("_mid1").set(mid30);
				}
			}
			
			if(mavgs.size()>5) graph();
			/*if(SampleStd.size()>5){				
				if(asd.vol_regress_r())
					container.getProbe("_vol").set( 1);
						//log("1");
					
				if(!asd.vol_regress_r())
					container.getProbe("_vol").set(-1);
					//log("1");
			}			
			*/
			order(RobotPrices.get(RobotPrices.size()-1)[0], SnowPrices.get(SnowPrices.size()-1)[0],
				  RobotPrices.get(RobotPrices.size()-1)[1], SnowPrices.get(SnowPrices.size()-1)[1]);
		}
		
		else{
			desiredRobotPrices[0]=-102; desiredRobotPrices[1]=1002; desiredSnowPrices[0]=-102;  desiredSnowPrices[1]=1002;
		}		
	}
	
	public double PnL(){
		double pnl=0;
		for(double[] order: orders){
			if(order[0]>0) pnl+=Math.max(prices.get(prices.size()-1)[0][1], prices.get(prices.size()-1)[1][1])-order[1];
			else           pnl+=order[1]-Math.min(prices.get(prices.size()-1)[0][0], prices.get(prices.size()-1)[1][0]);
		}
		
		return pnl;
	}
	
	
	public static void aprt(){	
		double val=0;
		double[] valuees=new double[20],
				 values =new double[20],
				 deve1s =new double[20],
				 deve2s =new double[20],
				 weight =new double[9 ],
				 dev1s  =new double[20],
				 dev2s  =new double[20];

		ex1s   =new double[10];
		ex2s	=new double[10];
		
		for(double i=.9; i>0; i-=.1)
			weight[(int) (9-i*10)]=i;
		 
		for(int j=0; j<30; j++){
			val=(lm[lm.length-1]+lm2[lm2.length-1])/2;
		
			for(int i=0; i<values.length; i++) 
				values[i]=val;
			
	        for(int i=0; i<10; i++){
	        	valuees[i]=getGaussian(0, underlying_vol);
	        	deve1s[i] =lm[i+1] -lm[i];
	        	deve2s[i] =lm2[i+1]-lm2[i];
	        }
	        
	        for(int i=10; i<20; i++){
	            values[i] =values[i-1] + valuees[i-10];
	            deve1s[i] =getGaussian(0, shock_std);
	        	deve2s[i] =getGaussian(0, shock_std);
	        }
	        
	        for(int i=10; i<20; i++){
			    if(i > 10){
			    	double temp1s[]=Arrays.copyOfRange(dev1s, i, i+9),
			    		   temp2s[]=Arrays.copyOfRange(dev2s, i, i+9);
			    	
			    	Arrays.sort(temp1s); Arrays.sort(temp2s);
			    	double sum1=0, sum2=0;
			    	for(int l=0; l<9; l++){
			    		sum1+=weight[l]*temp1s[l]; sum2+=weight[l]*temp2s[l];
			    	}
			    	
			        dev1s[i]=1.0*(deve1s[i-1]+ex_mem*sum1);
			        dev2s[i]=1.0*(deve2s[i-1]+ex_mem*sum2);
			    }
			    else{
			    	if(i==1){
			    		dev1s[i]=1.0*(deve1s[i]+0.75*deve1s[i-1]);
			        	dev2s[i]=1.0*(deve2s[i]+0.75*deve2s[i-1]);
			    	}
			    	else{
			    		dev1s[i]=1.0*(deve1s[i]+0.75*deve1s[i-1]+0.5*deve1s[i-2]);
				        dev2s[i]=1.0*(deve2s[i]+0.75*deve2s[i-1]+0.5*deve1s[i-2]);
			    	}
			    }
			}
	        
	        for(int i=0; i<ex1s.length; i++){
	        	ex1s[i]+=(values[i+10]+dev1s[i+10])/30;
	        	ex2s[i]+=(values[i+10]+dev2s[i+10])/30;
	        }
		}
	}

	public Quote[] refreshQuotes() {
		Quote[] quotes = new Quote[2];
		quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
		quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
		return quotes;
	}
	
	public void graph(){
		container.getProbe("_MA1  R").set(mavgs.get(mavgs.size()-5-1)[0][0]); 
		container.getProbe("_MA2  R").set(mavgs.get(mavgs.size()-5-1)[0][1]); 
		container.getProbe("_MA3  R").set(mavgs.get(mavgs.size()-5-1)[0][2]); 
		container.getProbe("_MA4  R").set(mavgs.get(mavgs.size()-5-1)[0][3]); 
		container.getProbe("_MA5  R").set(mavgs.get(mavgs.size()-5-1)[0][4]); 
		
		container.getProbe("_qb").set(desiredRobotPrices[0]); container.getProbe("_qa").set(desiredRobotPrices[1]);
		container.getProbe("_position").set(position);        container.getProbe("_p").set(PnL());
			
		container.getProbe("_mps").set((RobotPrices.get(RobotPrices.size()-1)[0]+
										RobotPrices.get(RobotPrices.size()-1)[1])/2);
		container.getProbe("_mpr").set((SnowPrices.get(SnowPrices.size()-1)[0]+
										SnowPrices.get(SnowPrices.size()-1)[1])/2);				
	}
	
	private static double getGaussian(double aMean, double aVariance){
		return aMean + r.nextGaussian() * aVariance;
	}
	
	
	public ArbCase getArbCaseImplementation() {
		return this;
	}

}