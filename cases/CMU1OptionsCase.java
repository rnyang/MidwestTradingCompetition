

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;
//import org.chicago.cases.options.OptionsUtil;






import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class CMU1OptionsCase extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();
	ArrayList<String> orderedSymbols = new ArrayList<String>(Arrays.asList(new String[]{
					"CMU1~RAND-20140527-80C","CMU1~RAND-20140527-90C","CMU1~RAND-20140527-100C",
	                "CMU1~RAND-20140527-110C","CMU1~RAND-20140527-120C",
	                "CMU1~RAND-20140627-80C","CMU1~RAND-20140627-90C","CMU1~RAND-20140627-100C",
	                "CMU1~RAND-20140627-110C","CMU1~RAND-20140627-120C"}));

	double globVol=0; double globIr=0.01;double timeStep=-1;double pnl=0;
	double[][] globS= new double[10][9];double ourPnl=0;
	double[] globStockPrice= new double[2]; double[] ourHoldings=new double[10];double ourStockHoldings=0;
	double riskDelta; double riskGamma; double riskVega;double riskDelta2;double riskGamma2;double riskVega2;
	double foreDelta; double foreGamma; double foreVega;
	double portDelta=0;double portGamma=0;double portVega=0;
	int hasGottenBA=0;int hasGottenRisk=0; int hasGottenFore=0;int hasGottenFilled=0;
	
	public void setUpMatrix(){
		//places the strikes in the first col
		for (int i = 0; i < 10; i++) {
			//for exp in may
			if(i<5){
				globS[i][0]=80+i*10;
				log("my strikes!!"+globS[i][0]);
				}
			//for exp in june 
			else{
				globS[i][0]=80+(i-5)*10;
				log("my strikes!!"+globS[i][0]);
				}
			}
	}
	
	public void addVariables(IJobSetup setup) {
		
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		log("HIHI im starting");
		setUpMatrix();
		log("setted up matrix");
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}
	

	public void newBidAsk(String idSymbol, double bid, double ask) {
		//here we are getting the new bid asks and we need to store it first
		//String lastPart=idSymbol.substring(idSymbol.length() - 1,idSymbol.length());
		knownSymbols.add(idSymbol);
		if(orderedSymbols.contains(idSymbol)){
			log("yes inside! saving"+idSymbol);
			int whichStock=orderedSymbols.indexOf(idSymbol);	
			log("whichStock:"+whichStock);
			globS[whichStock][1]=bid;globS[whichStock][2]=ask;
			//log("bid:"+globS[whichStock][0]);log("ask"+globS[whichStock][1]);
		}
		else{
			// It is the plain vanilla stock
			globStockPrice[0]=bid;globStockPrice[1]=ask;
		}
		hasGottenBA=1;
		//log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}
	//calculateDelta(double spot, double strike,double time,double rate,double vol) {}
	// get greeks for 10 options at each time period when the vol changes
	public void getGreeksandBS(){
		double timetoM=(100-timeStep)/365;
		double spot=(globStockPrice[0]+globStockPrice[1])/2;
		double strike=0;double isJune=0;
		//time to maturity is +30 for the 2nd set 
		for (int i = 0; i < 10; i++) {
			strike= globS[i][0];
			if(i>4){isJune=1;}
			double delta = Optionsutil.calculateDelta(spot,strike,timetoM+isJune*30,globIr,globVol);
			double gamma = Optionsutil.calculateGamma(spot,strike,timetoM+isJune*30,globIr,globVol);
			double vega = Optionsutil.calculateVega(spot,strike,timetoM+isJune*30,globIr,globVol);
			double Call=Optionsutil.Call(spot,strike,timetoM+isJune*30,globIr,globVol);
			globS[i][6]=delta;globS[i][7]=gamma;globS[i][8]=vega;globS[i][3]=Call;
			//profitBuy= intrinsic BS - ask >0 buy // profitSell= Bid - intrinsic BS>0 
			globS[i][4]= Call-globS[i][2];globS[i][5]=globS[i][1]-Call;
		}
			
		
		log("Yes I stored the greeks at this timeStep"+timeStep);
		// now we want the BS of the 
	}
	
	
	public void printBidAsk(){
		double bid1;double ask1;double strike1;
		log("in Print Bid Ask!!!!!!");
		for (int i=0; i <10; i++){
			strike1=globS[i][0];bid1=globS[i][1];ask1=globS[i][2];
			double delt=globS[i][6];double gamma= globS[i][7];double vega= globS[i][8];
			log("Option with strike:"+strike1+":bid:"+bid1+"ask:"+ask1);
			log("Greeks delt:"+delt+":Gamma"+gamma+"vega:"+vega);
		}
	}
	
	public void orderFilled(int volume, double fillPrice) {
		
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		hasGottenRisk=1;
		double riskDelta=msg.minDelta;double riskGamma= msg.minGamma;double riskVega=msg.minVega;
		double riskDelta2=msg.maxDelta;double riskGamma2= msg.maxGamma;double riskVega2=msg.maxVega;
		log("I received a new Risk delta:"+riskDelta+"-"+riskDelta2
				+"gamma:"+riskGamma+"-"+riskGamma2+"vega:"+riskVega+"-"+riskVega2+"RRRRRRRRRR");
		log("time is "+timeStep);
	}

	public void newForecastMessage(ForecastMessage msg) {
		hasGottenFore=1;
		foreDelta=msg.delta;foreGamma= msg.gamma;foreVega=msg.vega;
		log("I received a forecast message:delta"+foreDelta+"gamma:"+foreGamma+"Vega:"+foreVega+"FFFFFFFF");
		log("time is "+timeStep);
	}
	
	
	public void newVolUpdate(VolUpdate msg) {
		globVol= msg.impliedVol;
		//double b = Optionsutil.calculateDelta(100,80,10,0.1,a);
		log("I received a vol update message!"+globVol+"VVVVVVVVVVVVVV");
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		timeStep++;
		log("Placing orders and time is "+timeStep);
		//log("Getting Greeks");
		//getGreeks();
		if(hasGottenBA==1){log("yooooooooooooooooooooo!!!!!!");getGreeksandBS();}
		//if(hasGottenFore==0){printPnl();}
		if(hasGottenFore==0){printPnl();}
		
			else {//here we are ordering
			printPnl();
			updateHoldingGreeks();
			log("portGreeks"+portDelta+"gam"+portGamma+"veg"+portVega);
			log("FORESKINI"+foreDelta+"gam"+foreGamma+"veg"+foreVega);
			double diffDelta=foreDelta-portDelta;double diffGamma=foreGamma-portGamma;double diffVega=foreVega-portVega;
			log("diffGreeks"+diffDelta+"gam"+diffGamma+"veg"+diffVega);
			
			OptionPair optPair = OptionPicker.pickOptionPair(globS,new double[] {diffDelta,diffGamma,diffVega});

			
			int opt1Amt = (int) (optPair.opt1Amt);
			int opt2Amt = (int) (optPair.opt2Amt);
			int opt1Index = optPair.opt1Index;
			int opt2Index = optPair.opt2Index;
			String opt1Name=orderedSymbols.get(opt1Index);
			String opt2Name=orderedSymbols.get(opt2Index);
			
			
			log("opt1"+opt1Amt);
			log("opt2"+opt2Amt);
			double stockBought=foreDelta-(portDelta+opt1Amt*globS[opt1Index][6]+opt2Amt*globS[opt2Index][6]);
			
			
			
			OrderInfo[] orders = new OrderInfo[3];
			if(opt1Amt>0){
				orders[0]=new OrderInfo(opt1Name,OrderSide.BUY,globS[opt1Index][2],Math.min(Math.max(opt1Amt, -2), 3));
				ourPnl-=opt1Amt*-globS[opt1Index][2];
			}
			else{
				orders[0]=new OrderInfo(opt1Name,OrderSide.SELL,globS[opt1Index][1],Math.abs(Math.min(Math.max(opt1Amt, -2), 2)));	
				ourPnl+=opt1Amt*globS[opt1Index][1];
			}
			if(opt2Amt>0){
				orders[1]=new OrderInfo(opt2Name,OrderSide.BUY,globS[opt2Index][2],Math.min(Math.max(opt2Amt, -2), 4));
				ourPnl-=opt1Amt*globS[opt2Index][1];
				
			}
			else{
				orders[1]=new OrderInfo(opt2Name,OrderSide.SELL,globS[opt2Index][1],Math.abs(Math.min(Math.max(opt2Amt, -2), 2)));
				ourPnl+=opt1Amt*globS[opt2Index][1];
				}
			//	orders[i] = new OrderInfo(symbol, OrderSide.BUY, 100.00, 10);
			if(stockBought>0){
				orders[2]=new OrderInfo(orderedSymbols.get(0),OrderSide.BUY,globStockPrice[0],(int)Math.min(Math.max(stockBought, -2), 1));
				ourPnl-=opt1Amt*globStockPrice[0];
				}
			else{
				orders[2]=new OrderInfo(orderedSymbols.get(0),OrderSide.SELL,globStockPrice[1],(int)Math.abs(Math.min(Math.max(stockBought, -2), 3)));
				ourPnl+=opt1Amt*globStockPrice[1];
				}
			return orders;
		}
			return new OrderInfo[0];
	}
	
	public void printPnl(){
		//calculates the current holdings and the current pnl of the system. So we know what we have when we 
		// liquidate it mark to market
		for(int i =0; i<10;i++){
			log("We have of strike:"+globS[i][0]+"a quantity of"+ourHoldings[i]);
			pnl=globS[i][1]*ourHoldings[i];
		}
			
			log("Also, we have the underlying in quantity of"+ourStockHoldings);
			log("ourPNL is"+pnl+ourPnl+"kkkkkkkkkkkkkkkkkk");
	}
	
	public void updateHoldingGreeks(){
		
		for (int i =0;i<10;i++){
			double quant=ourHoldings[i];
			portDelta=quant*globS[i][6];portGamma=quant*globS[i][7];portVega=quant*globS[i][8];
		}
	}
	
	public void orderFilled(String idSymbol, double price, int quantity) {
		hasGottenFilled=1;
		if(orderedSymbols.contains(idSymbol)){
			int i = orderedSymbols.indexOf(idSymbol);
			ourHoldings[i]=ourHoldings[i]+quantity;
		}
		else{
			//our stock got filled 		
			ourStockHoldings+=quantity;
		}
		//pnl=pnl-price*(double)(quantity);
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity+"loookiee");
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}


}
