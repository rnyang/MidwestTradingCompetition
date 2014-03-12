

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

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;

import java.lang.Math.*;

import Jama.*;

public class CHI3OptionCase extends AbstractOptionsCase implements OptionsCase{
		private IDB myDatabase;
	int factor;
	String idSymbol = "RAND";
	double time = 100;
	double pDelta = 0.0;
	double pGamma = 0.0;
	double pVega = 0.0;
	//double [] portfoliogreeks = new double[3];
	//portfoliogreeks = {pDelta, pGamma, pVega};
	/*double [] portfoliogreeks;
	portfoliogreeks = new double[3];
	portfoliogreeks = {pDelta, pGamma, pVega};
	/*
	portfoliogreeks[0] = pDelta;
	portfoliogreeks[1] = pGamma;
	portfoliogreeks[2] = pVega;
	*/
	private List<String> knownSymbols = new ArrayList<String>();

	public double[] order_set(RiskMessage r_msg, ForecastMessage f_msg, VolUpdate msg){
		double delta = 0;
		double gamma =0;
		double vega = 0;
		if (f_msg.delta >= r_msg.minDelta && f_msg.delta <= r_msg.maxDelta){     
			delta = f_msg.delta - pDelta;
			}
			else if (f_msg.delta <= r_msg.minDelta){
				delta = r_msg.minDelta + (r_msg.minDelta*.15) -pDelta;
			}
			else if (f_msg.delta > r_msg.maxDelta){
				delta = r_msg.maxDelta*.85 -pDelta;
			}

		if (f_msg.gamma >= r_msg.minGamma && f_msg.gamma <= r_msg.maxGamma){
			gamma = f_msg.gamma - pGamma;
			}
			else if (f_msg.gamma <= r_msg.minGamma){
				gamma = r_msg.minGamma + (r_msg.minGamma*.15) - pGamma;
			}
			else if (f_msg.gamma > r_msg.maxGamma){
				gamma = r_msg.maxGamma*.85 - pGamma;
			}

		if (f_msg.vega >= r_msg.minVega && f_msg.vega <= r_msg.maxVega){
			vega = f_msg.vega - pVega;
			}
			else if (f_msg.vega <= r_msg.minVega){
				vega = r_msg.minVega + (r_msg.minVega*.15) - pVega;
			}
			else if (f_msg.vega > r_msg.maxVega){
				vega = r_msg.maxVega*.85 -pVega;
			}
		log("delta is " + delta);
		log("gamma is " + gamma);
		log("vega is " + vega);

		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		double [][] target_vals = {{delta},{gamma},{vega}};
		Matrix b = new Matrix(target_vals);
		double [][] options_risk = new double[3][10];
			for (int i = 0; i < 10; i++){
				double cDelta = Optionsutil.calculateDelta(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				double cGamma = Optionsutil.calculateGamma(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				double cVega = Optionsutil.calculateVega(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				options_risk[i][0] = cDelta;
				options_risk[i][1] = cGamma;
				options_risk[i][2] = cVega;
			}
		//Let's assume that we have an array of greeks of available options
		//we'd make it a public class. Not sure about line directly below.
		Matrix a = new Matrix(options_risk); 
		Matrix sol_matrix = a.solve(b);
		double [][] sol = sol_matrix.getArray(); 
		double option1_val = Math.ceil(sol[0][0]);
		double option2_val = Math.ceil(sol[0][1]);
		double option3_val = Math.ceil(sol[0][2]);
		double option4_val = Math.ceil(sol[0][3]);
		double option5_val = Math.ceil(sol[0][4]);
		double option6_val = Math.ceil(sol[0][5]);
		double option7_val = Math.ceil(sol[0][6]);
		double option8_val = Math.ceil(sol[0][7]);
		double option9_val = Math.ceil(sol[0][8]);
		double option10_val = Math.ceil(sol[0][9]);
		double [] int_sol = {option1_val,option2_val,option3_val,option4_val,
						  option5_val,option6_val,option7_val,option8_val,
						  option9_val,option10_val};
	
		return int_sol;
		}
	

	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		knownSymbols.add(idSymbol);
		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}

	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage r_msg) {
		log("I received an admin message!");
	}

	public void newForecastMessage(ForecastMessage f_msg) {
		log("I received a forecast message!");
	}
	
	public void newVolUpdate(VolUpdate msg) {
		log("I received a vol update message!");
		time = time-1;
		log("time = " + time);
	}	
	public void penaltyFill(String idSymbol, double price, int quantity){
		log("Penalty called...oh no!");
	}
	
	public OrderInfo[] placeOrders(ForecastMessage f_msg, RiskMessage r_msg, VolUpdate msg){
			log("Placing orders");
			log("knownSymbols size = " + knownSymbols.size());
			double[]options_buy = order_set(r_msg, f_msg, msg);
			OrderInfo[] orders = new OrderInfo[options_buy.length];
			InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
			for (int i=0; i < options_buy.length; i++){
				String symbol = knownSymbols.get(i);
				orders[i] = new OrderInfo(symbol, OrderSide.BUY, 10.0, (int) options_buy[i]);
			}

			for (int i = 0; i < knownSymbols.size(); i++){
				pDelta += Optionsutil.calculateDelta(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				pGamma += Optionsutil.calculateGamma(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				pVega += Optionsutil.calculateVega(100.0, details.strikePrice, time/365.0, .01, msg.impliedVol);
				//portfoliogreeks[0] = pDelta;
				//portfoliogreeks[1] = pGamma;
				//portfoliogreeks[2] = pVega;
			}
			return orders;


		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		/*log("Placing orders");
		OrderInfo[] orders = new OrderInfo[knownSymbols.size()];
		for (int i = 0; i < knownSymbols.size(); i++) {
			String symbol = knownSymbols.get(i);
			orders[i] = new OrderInfo(symbol, OrderSide.BUY, 100.00, 10);
		}
		return orders;  */
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}


	public OptionsCase getOptionCaseImplementation(){
		return this;
	}


	@Override
	public OrderInfo[] placeOrders() {
		// TODO Auto-generated method stub
		return null;
	}


}
	


