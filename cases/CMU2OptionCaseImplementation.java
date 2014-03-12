
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class CMU2OptionCaseImplementation extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	private List<String> knownSymbols = new ArrayList<String>();

	ForecastMessage fm;
	RiskMessage rm;
	VolUpdate vm;
	
	private HashMap<String,OptionInfo> optiondata = new HashMap<String,OptionInfo>();
	
	int time = 0;
	double moneySpent = 0;
	int numOrders = 0;
	
	double netDelta;
	double netVega;
	double netGamma;
	
	boolean messageOn = true;
	boolean change = false;
	
	final String underlying = "CMU2~RAND-E";
	int message = 0;

	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void logM(String msg){
		if((messageOn)){
			log(msg);
			message++;
		}
	}
	
	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		time = 0;
		netDelta = 0;
		netVega = 0;
		netGamma = 0;
		moneySpent = 0;
		optiondata = new HashMap<String,OptionInfo>();
		
		for(int i=0; i<5; i++)
			log("");
		logM("Start");
		optiondata.put(underlying, new OptionInfo(0,0,101));
		//messageOn = false;
	}
	
	
	
	private double targetDelta(){
		if(fm.delta < rm.minDelta)
			return rm.minDelta;
		if(fm.delta > rm.maxDelta)
			return rm.maxDelta;
		return fm.delta;
	}
	
	private double targetGamma(){
		if(fm.gamma < rm.minGamma)
			return rm.minGamma;
		if(fm.gamma > rm.maxGamma)
			return rm.maxGamma;
		return fm.gamma;
	}
	
	private double targetVega(){
		if(fm.vega < rm.minVega)
			return rm.minVega;
		if(fm.vega > rm.maxVega)
			return rm.maxVega;
		return fm.vega;
	}
	
	public void newBidAsk(String idSymbol, double bid, double ask) {
		if(!knownSymbols.contains(idSymbol))
			knownSymbols.add(idSymbol);
		OptionInfo target = optiondata.get(idSymbol);
		if(target==null)
			optiondata.put(idSymbol, new OptionInfo(bid,ask,100));
		else{	
			target.bid = bid;
			target.ask = ask;
			target.daysLeft --;
		}
	}

	public void orderFilled(int volume, double fillPrice) {
		logM("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		rm = msg;
		logM("Risk G: ("+msg.minGamma+","+msg.maxGamma+")"+" D: ("+msg.minDelta+","+msg.maxDelta+") V: ("+msg.minVega+","+msg.maxVega+")");
		change = true;
	}

	public void newForecastMessage(ForecastMessage msg) {
		fm = msg;
		logM("Forecast G:"+msg.gamma+" D:"+msg.delta+" V:"+msg.vega);
		change = true;
	}
	
	public void newVolUpdate(VolUpdate msg) {
		vm = msg;
		time++;
		log("");
		log("Time: "+time);
		logM("Vol: "+vm.impliedVol);
		
	
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		updateGreeks();
		printG();
		logM("PENALTY! "+idSymbol+" "+price+" "+quantity);
		OptionInfo option = optiondata.get(idSymbol);
		option.position += quantity;
		moneySpent += price * quantity;
		updateGreeks();
		pnl();
		
	}

	public OrderInfo[] placeOrders() {
		
		ArrayList<OrderInfo> oi = new ArrayList<OrderInfo>();
		OptionInfo EStock = optiondata.get(underlying);
		logM("Underlying Stock "+EStock.bid+" "+EStock.ask);
		if(time > 1){
			updateGreeks();
			printG();
		}else
			change = false;
		if(time == 99){
			liquidate(oi);
		
			
			logM("Forecast G:"+fm.gamma+" D:"+fm.delta+" V:"+fm.vega);
			logM("Risk G: ("+rm.minGamma+","+rm.maxGamma+")"+" D: ("+rm.minDelta+","+rm.maxDelta+") V: ("+rm.minVega+","+rm.maxVega+")");

			return toArray(oi);
		}else if(time == 100){
			pnl();
			return new OrderInfo[0];
		}

		numOrders = 0;
		String bestbuy = null, bestsell = null;
		double maxBuy = -1000, maxSell = -1000;
		for(String id: optiondata.keySet()){
			if(!id.equals(underlying)){
				if(bestbuy == null){
					bestbuy = id;
					bestsell = id;
				}else{
					OptionInfo option = optiondata.get(id);
					double value = getGreeks(id).call;
					double valueBuy = value - option.ask;
					double valueSell = option.bid - value;
					if(valueBuy > maxBuy && option.ask > 0.05){
						maxBuy = valueBuy;
						bestbuy = id;
					}else if(valueSell > maxSell && option.bid > 0){
						maxSell = valueSell;
						bestsell = id;
					
					}		
				}
			}
		}
		
		double bestSituation = Integer.MAX_VALUE;
		int bestQty = 0;
		GreekInfo g = getGreeks(bestbuy);
		for(int i=0; i<1000; i++){
			double sit = situation(netGamma + g.gamma*i, netDelta + g.delta*i, netVega + g.vega*i);
			if(sit < bestSituation){
				bestSituation = sit;
				bestQty = i;
			}
		}
		g = getGreeks(bestsell);
		for(int i=0; i<1000; i++){
			double sit = situation(netGamma - g.gamma*i, netDelta - g.delta*i, netVega - g.vega*i);
			if(sit < bestSituation){
				bestSituation = sit;
				bestQty = i*(-1);
			}
		}
		logM("Best: "+bestSituation+" "+bestQty);
		if(change && time > 1){
			liquidate(oi);
			checkUnderlying(oi);
			change = false;
		}else if(bestSituation > 10000 && !change){
			liquidate(oi);
		}else if(!change){
			if(bestQty > 0){
				transaction(oi,bestbuy,bestQty);
			}else if(bestQty < 0){
				transaction(oi,bestsell,bestQty);
			}
		}
		
		if(time == 1)
			oi.add(new OrderInfo(underlying,OrderSide.BUY,EStock.ask,25));
	
		return toArray(oi);
	}

	private OrderInfo[] toArray(ArrayList<OrderInfo> oi){
		OrderInfo[] array = new OrderInfo[oi.size()];
		for(OrderInfo o: oi){
			array[numOrders++] = o;
		}
		
		
		return array;
	}
	
	private void liquidate(ArrayList<OrderInfo> oi){
		logM("Liquidating...");
		for(String id: optiondata.keySet()){
			OptionInfo option = optiondata.get(id);
			if(option.position > 0 && option.bid > 0)
				oi.add(new OrderInfo(id,OrderSide.SELL,option.bid,option.position));
			else if(option.position < 0 && option.ask > 0)
				oi.add(new OrderInfo(id,OrderSide.BUY,option.ask,option.position * (-1)));
		}
		netGamma = 0; netDelta = 0; netVega  = 0;
	}
	
	private void transaction(ArrayList<OrderInfo> oi, String id, int quantity){

		OptionInfo option = optiondata.get(id);
		if(quantity > 0)
			oi.add(new OrderInfo(id,OrderSide.BUY,option.ask,quantity));
		else if(quantity < 0)
			oi.add(new OrderInfo(id,OrderSide.SELL,option.bid,(-1)*quantity));
		
		GreekInfo greeks = getGreeks(id);
		netGamma += greeks.gamma*quantity;
		netDelta += greeks.delta*quantity;
		netVega += greeks.vega*quantity;	
	}
	
	private void checkUnderlying(ArrayList<OrderInfo> oi){
		OptionInfo EStock = optiondata.get(underlying);
		
		if(change){
			logM("Checking Underlying");
			change = false;
			double delta = targetDelta();
			if(netDelta > delta)
				oi.add(new OrderInfo(underlying,OrderSide.SELL,EStock.bid,(int)(netDelta - delta)));
			else if(netDelta < delta)
				oi.add(new OrderInfo(underlying,OrderSide.BUY,EStock.ask,(int)(delta - netDelta)));
			netDelta += (int)(delta - netDelta);
			

		}
	}
	
	private void updateGreeks(){
		double g=0,v=0,d=0;
		for(String id: optiondata.keySet()){
		
			OptionInfo option = optiondata.get(id);
			if(!id.equals(underlying)){
				
				GreekInfo greeks = getGreeks(id);
				
				g+=greeks.gamma*option.position;
				v+=greeks.vega*option.position;
				d+=greeks.delta*option.position;
			}else
				d+=option.position;
			
		}
		
		netDelta = d;
		netGamma = g;
		netVega = v;
	}
	
	private GreekInfo getGreeks(String id){
		OptionInfo EStock = optiondata.get(underlying);
		double spot =  (EStock.bid+EStock.ask)/2;
		InstrumentDetails details = instruments().getInstrumentDetails(id);
		double timeLeft = (130-time)/365.0;
		
		if(id.indexOf("0527")>0)
			timeLeft = (100-time)/365.0;
		double callPrice = Optionsutil.Call(spot, details.strikePrice, timeLeft, 0.01, vm.impliedVol);
		double gamma = Optionsutil.calculateGamma(spot, details.strikePrice, timeLeft, 0.01, vm.impliedVol);
		double delta = Optionsutil.calculateDelta(spot, details.strikePrice, timeLeft, 0.01, vm.impliedVol);
		double vega = Optionsutil.calculateVega(spot, details.strikePrice, timeLeft, 0.01, vm.impliedVol);
		return new GreekInfo(gamma, delta, vega,callPrice);
	}
	
	private void printG(){
		logM("Gamma: "+netGamma+" Delta: "+netDelta+" Vega: "+netVega);
	}

	private void pnl(){
		double value = 0;
		OptionInfo EStock = optiondata.get(underlying);
		double Ebuy = EStock.bid;
		for(String id: optiondata.keySet()){
			OptionInfo option = optiondata.get(id);
			if(id.equals(underlying)){
				value += option.position * Ebuy;
			}
			else{
				double optionVal = getGreeks(id).call;
				value += option.position * optionVal;
			}
		}
		
		logM("Assets: "+value);
		logM("Spent: "+moneySpent);
		logM("Net "+(value-moneySpent));
		printG();
		
	}
	
	private double situation(double gamma, double delta, double vega){
	
		double gRange = rm.maxGamma - rm.minGamma;
		double dRange = rm.maxDelta - rm.minDelta;
		double vRange = rm.maxVega - rm.minVega;
		double gS = Math.pow(gamma - targetGamma(), 2)/Math.pow(gRange,2);
		double dS = Math.pow(delta - targetDelta() , 2)/Math.pow(dRange,2);
		double vS = Math.pow(vega - targetVega(), 2)/Math.pow(vRange,2);
		if(gamma < rm.minGamma)
			gS += 1000000000+4*(rm.minGamma - gamma)/gRange;
		if(gamma > rm.maxGamma)
			gS += 1000000000+4*(gamma -rm.maxGamma)/gRange;
		if(delta < rm.minDelta)
			dS += 1000000000+4*(rm.minDelta - delta)/dRange;
		if(delta > rm.maxDelta)
			dS += 1000000000+4*(delta -rm.maxDelta)/dRange;
		if(vega < rm.minVega)
			vS += 1000000000+4*(rm.minVega - vega)/vRange;
		if(vega > rm.maxVega)
			vS += 1000000000+4*(vega -rm.maxVega)/vRange;
		return gS + dS + vS;
	}
	
	public void orderFilled(String id, double price, int quantity) {
		OptionInfo data = optiondata.get(id);
		
		moneySpent += price * quantity;
		data.position += quantity;
		
		logM("My order for " + id + " got filled at " + price + " with quantity of " + quantity);
		numOrders --;
		if(numOrders == 0){
			updateGreeks();
			pnl();
		}
	}
	
	public OptionsCase getOptionCaseImplementation() {
		return this;
	}
	
	class GreekInfo{
		double gamma;
		double delta;
		double vega;
		double call;
		public GreekInfo(double g, double d, double v,double c){
			gamma = g; delta = d; vega = v; call = c;
		}
	}
	
	class OptionInfo{
	
		double bid;
		double ask;
		double daysLeft;
		int position;
		
		public OptionInfo(double b, double a, double time){
			daysLeft = time;
			bid=b;
			ask=a;
			position = 0;
		}
	}
	
}
