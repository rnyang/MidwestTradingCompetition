

import java.util.ArrayList;
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


import java.util.*;

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class CAL1OptionCase extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int stop;
	int t=100;
	double currETF; // current ETF trading price
	Map<String, Double> prices = new HashMap<String, Double>();
	double mindelta, mingamma, minvega, maxdelta, maxgamma, maxvega; // These are the risk limits for my portfolio
	double fdelta, fgamma, fvega; // These are my forecast parameters that I want to achieve
	double tdelta, tgamma, tvega;
	double vol, cash;
	String ETF = "CAL1~RAND-E";
	String May80 = "CAL1~RAND-20140527-80C";
	String May90 = "CAL1~RAND-20140527-90C";
	String May100 = "CAL1~RAND-20140527-100C";
	String May110 = "CAL1~RAND-20140527-110C";
	String May120 = "CAL1~RAND-20140527-120C";
	String June80 = "CAL1~RAND-20140627-80C";
	String June90 = "CAL1~RAND-20140627-90C";
	String June100 = "CAL1~RAND-20140627-100C";
	String June110 = "CAL1~RAND-20140627-110C";
	String June120 = "CAL1~RAND-20140627-120C";
	String[] knownSymbols = {ETF, May80, May90, May100, May110, May120, June80, June90, June100, June110, June120};
	Map<String, Double> fair = new HashMap<String, Double>();
    int[] position = new int[11];
    Map<String,Integer> hashPos = new HashMap<String, Integer>(){{
        put(ETF,0); 
        put(May80,0);
        put(May90,0);
        put(May100,0);
        put(May110,0);
        put(May120,0);
        put(June80,0);
        put(June90,0);
        put(June100,0);
        put(June110,0);
        put(June120,0);
     }};
    int update = 0;
	
	
	
	public void addVariables(IJobSetup setup) {
		setup.addVariable("STOP", "Default is zero. Type 1 to stop", "int", "0");
	}
	

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		// helper method for accessing declared variables
		stop = getIntVar("STOP"); 
	}
	
	public void newBidAsk(String idSymbol, double bid, double ask) {
		prices.put(idSymbol+"bid", new Double(bid));
		prices.put(idSymbol+"ask", new Double(ask));
		// InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		// details.
	}

	public void newRiskMessage(RiskMessage msg) {
		maxdelta = msg.maxDelta;
		maxgamma = msg.maxGamma;
		maxvega = msg.maxVega;
		mindelta = msg.minDelta;
		mingamma = msg.minGamma;
		minvega = msg.minVega;
		updateTargetGreeks();
	}
	
	public void updateTargetGreeks(){
		if (fdelta > maxdelta){
			tdelta = maxdelta;
		}
		else if (fdelta < mindelta){
			tdelta = mindelta;
		}
		else {tdelta = fdelta;}
		if (fgamma > maxgamma){
			tgamma = maxgamma;
		}
		else if (fgamma < mingamma){
			tgamma = mingamma;
		}
		else {tgamma = fgamma;}
		if (fvega > maxvega){
			tvega = maxvega;
		}
		else if (fvega < minvega){
			tvega = minvega;
		}
		else {tvega = fvega;}
		update = 1;
		log("THERE WAS A FUCKING UPDATE");
	}

	public void newForecastMessage(ForecastMessage msg) {
		fdelta = msg.delta;
		fgamma = msg.gamma;
		fvega = msg.vega;
		updateTargetGreeks();
	}
	
	public void newVolUpdate(VolUpdate msg) {
		vol = msg.impliedVol;
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {
		int[] new_pos = new int[11];
		double[] dist = new double[11];
		OrderInfo[] orders = new OrderInfo[11];
		if (stop == 0){
			if (t < 100 && update == 1){
				currETF = (prices.get(ETF+"bid") +  prices.get(ETF+"ask"))/2;
				for (int i = 1; i < knownSymbols.length; i++) {
					String symbol = knownSymbols[i];
					InstrumentDetails details = instruments().getInstrumentDetails(symbol);
					fair.put(symbol, Optionsutil.Call(currETF, details.strikePrice, (double)(t/365.), 0.01, vol));
					if (fair.get(symbol) < prices.get(symbol+"ask") && fair.get(symbol) > prices.get(symbol+"bid")){
						if (fair.get(symbol) - prices.get(symbol+"bid") <= prices.get(symbol+"ask") - fair.get(symbol) ){
							dist[i] = prices.get(symbol+"bid") - fair.get(symbol); //this will be negative want max of these negatives
							
						}
						else {
							dist[i] = prices.get(symbol+"ask") - fair.get(symbol); //this will be positive want min of these positives
						}
					}
	
				}
	
				if (tgamma > 0) {
					double curr_min = 0;
					int index = 0;
					log("distance = "+ dist[0] + dist[1]+ dist[2]+ dist[3]+ dist[4]+ dist[5]+ dist[6]+ dist[7]+ dist[8]+ dist[9]+ dist[10]);
					for (int i = 0; i < dist.length; i++){
						if (dist[i] > 0){
							curr_min = dist[i];
							index = i;
						}
						else if (curr_min > 0){
							if ((dist[i] > 0) && (dist[i] < curr_min)){
								curr_min = dist[i];
								index = i;
							}
						}
					}
					double[] greeks = calcRisk(new_pos);
					double[] greekscurr = calcRisk(position);
					int count = 0;
					while ((greeks[1] < tgamma) && (Math.abs(greeks[2]) < maxvega)){
						count = count + 1;
						new_pos[index] = count;
						greeks = calcRisk(new_pos);
					}
					new_pos[index] = count-1;
					log("new pos = "+ new_pos[0] + new_pos[1]+ new_pos[2]+ new_pos[3]+ new_pos[4]+ new_pos[5]+ new_pos[6]+ new_pos[7]+ new_pos[8]+ new_pos[9]+ new_pos[10]);
					for (int j = 0; j < position.length; j++){
						if (j == 0) {
							if (greeks[0]-tdelta >= 0){
								orders[0] = new OrderInfo(ETF, OrderSide.SELL, 0.001, (int) (Math.floor(Math.abs(greeks[0] - tdelta + greekscurr[0]))));
							}
							else {
								orders[0] = new OrderInfo(ETF, OrderSide.BUY, 1000, (int) (Math.floor(Math.abs(greeks[0] - tdelta + greekscurr[0]))));
							}
						}
						else {
							if (new_pos[j] - position[j] >= 0){
								orders[j] = new OrderInfo(knownSymbols[j], OrderSide.BUY, 1000, (int)(Math.floor(new_pos[j] - position[j])));				
							}
							else {
								orders[j] = new OrderInfo(knownSymbols[j], OrderSide.SELL, 0.001, (int)(Math.floor(Math.abs(new_pos[j] - position[j]))));
							}
						}
					}
					
				}
				else if (tgamma < 0){
					double curr_min = 0;
					int index = 0;
					
					for (int i = 0; i < dist.length; i++){
						if (dist[i] < 0){
							curr_min = dist[i];
							index = i;
						}
						else if (curr_min < 0){
							if ((dist[i] < 0) && (dist[i] > curr_min)){
								curr_min = dist[i];
								index = i;
							}
						}
					}
					log("curr min = " + curr_min + "and index " + index);
					double[] greeks = calcRisk(new_pos);
					double[] greekscurr = calcRisk(position);
					int count = 0;
					while ((greeks[1] > tgamma) && (Math.abs(greeks[2]) < Math.abs(tvega))){
						count = count - 1;
						new_pos[index] = count;
						greeks = calcRisk(new_pos);
					}
					new_pos[index] = count + 1;
					for (int j = 0; j < position.length; j++){
						if (j == 0){
							if (greeks[0]-tdelta >= 0){
								orders[0] = new OrderInfo(ETF, OrderSide.SELL, 0.001, (int) (Math.floor(Math.abs(greeks[0] - tdelta + greekscurr[0]))));
							}
							else {
								orders[0] = new OrderInfo(ETF, OrderSide.BUY, 1000, (int) (Math.floor(Math.abs(greeks[0] - tdelta + greekscurr[0]))));
							}
						}
						else {
							if (new_pos[j] - position[j] >= 0){
								orders[j] = new OrderInfo(knownSymbols[j], OrderSide.BUY, 1000, (int)(Math.floor(new_pos[j] - position[j])));				
							}
							else {
								orders[j] = new OrderInfo(knownSymbols[j], OrderSide.SELL, 0.001, (int)(Math.floor(Math.abs(new_pos[j] - position[j]))));
							}
						}
						
					}
					
				}
				update = 0;
			}
			else if (t < 100 && update == 0) {
				for (int k = 0; k < knownSymbols.length; k++) {
					if (position[k] > 0) {
						orders[k] = new OrderInfo(knownSymbols[k], OrderSide.SELL, 0.001, (int)(Math.abs(Math.floor(position[k]/2))));
					}
					else {
						orders[k] = new OrderInfo(knownSymbols[k], OrderSide.BUY, 1000, (int)(Math.abs(Math.floor(position[k]/2))));
					}
				}
			}
/*			log("current pos = "+ position[0] + " , " + position[1]  + " , " + position[2]  + " , " + position[3]  + " , " + position[4]  + " , " + position[5]  + " , " + position[6]  + " , " + position[7]  + " , " + position[8]  + " , " + position[9]  + " , " + position[10]);
			log("t = " + t);*/
		}
		t = t - 1;
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		if (quantity < 0) {
			cash = cash - price*quantity; // selling means we get cash
		}
		else {
			cash = cash + price*quantity;
		}
		int temp = hashPos.get(idSymbol);
		hashPos.put(idSymbol, temp + quantity);
		for (int j = 0; j < knownSymbols.length; j++){
			position[j] = hashPos.get(knownSymbols[j]);
		}
		calcRisk(position);
	}
	
	public double[] calcRisk(int[] pos){
		double[] greeks = new double[3];
		currETF = (prices.get(ETF+"bid") +  prices.get(ETF+"ask"))/2;
		for (int i = 1; i < pos.length; i++){
			String symbol = knownSymbols[i];
			InstrumentDetails details = instruments().getInstrumentDetails(symbol);
			if (pos[i] != 0){
				greeks[0] = greeks[0] + pos[i]* Optionsutil.calculateDelta(currETF, details.strikePrice, (double)(t/365.), 0.01, vol);
				greeks[2] = greeks[2] + pos[i]*Optionsutil.calculateVega(currETF, details.strikePrice, (double)(t/365.), 0.01, vol);
				greeks[1] = greeks[1] + pos[i]*Optionsutil.calculateGamma(currETF, details.strikePrice, (double)(t/365.), 0.01, vol);
			}
		}
		greeks[0] = greeks[0] + pos[0];
		log("current pos = "+ pos[0] + " , " + pos[1]  + " , " + pos[2]  + " , " + pos[3]  + " , " + pos[4]  + " , " + pos[5]  + " , " + pos[6]  + " , " + pos[7]  + " , " + pos[8]  + " , " + pos[9]  + " , " + pos[10]);
		log("current greeks are delta = " + greeks[0] + " , gamma = " + greeks[1] + " , vega = " + greeks[2]);
		log("min and max greeks are delta = " + mindelta + " , " + maxdelta + "  gamma = " + mingamma + " , " + maxgamma + " , and vega = " + minvega + " , " + maxvega);
		log("forecase greeks are delta = " + fdelta + " , gamma = " + fgamma + " , and vega = " + fvega);
		return greeks;
	}
	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
