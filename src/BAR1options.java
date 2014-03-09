

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ProcessPenaltyRequest;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo.OrderSide;

import Jama.Matrix;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;
/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */



public class BAR1options extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private HashMap<String,Double> time = new HashMap<String,Double>();
	private Portfolio p = new Portfolio();
	private ArrayList<String> Symbols = new ArrayList<String>();
	private double ForcastDelta = 0, ForcastGamma = 0, ForcastVega = 0; 
	private double[] RiskMetrics = new double[6];
	public String Underline;
	public String HundredMay;
	public String HundredJune;
	//riskmetrics = [mindelta,maxdelta, mingamma, maxgamma, minvega, maxvega]
	
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database; 
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}
	
	//values = [bid, ask, strike, time]
	public void newBidAsk(String idSymbol, double bid, double ask) {	
		//log("got new price for: " + idSymbol);
		if(!Symbols.contains(idSymbol))
			Symbols.add(idSymbol);
		
		//Get details on the instrument 
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		
		if(!details.type.isOption()) {
			p.SetSpot((bid+ask)/2);
			Underline = idSymbol;
		}
		
		if(details.strikePrice == 100 && details.expiration.toString().toLowerCase().contains("may"))
			HundredMay = idSymbol; 
		if(details.strikePrice == 100 && details.expiration.toString().toLowerCase().contains("jun"))
			HundredJune = idSymbol; 
		
		//------------------------------------------------------------------
		//------------------Time Set-----------------------------------------
		if(!time.containsKey(idSymbol)){
			if(details.type.isOption()){
				if(details.expiration.toString().toLowerCase().contains("jun"))
					time.put(idSymbol, 129.0);
				else if(details.expiration.toString().toLowerCase().contains("may"))
					time.put(idSymbol, 99.0);
			} else time.put(idSymbol, 99.0);
		} else {
			time.put(idSymbol,time.get(idSymbol)-1.0);
		}
		//-------------------------------------------------------------------
		//---------------------------------------------------------------------
		//----------- upload bid,ask, strike, and time to data 
		double[] values = new double[4];
		values[0] = bid; values[1] = ask; values[2] = (details.strikePrice); values[3] = (double)(time.get(idSymbol));
		p.UpdatePrice(idSymbol, values);
		///----------------------------------------------------------------------
		//--------------------------------------------------------------------------
		
		
	}

	public void newRiskMessage(RiskMessage msg) {
		//Initialize risk metrics [mindelta,maxdelta, mingamma, maxgamma, minvega, maxvega]
		//log("I received an admin message!");
		RiskMetrics[0] = msg.minDelta;
		RiskMetrics[1] = msg.maxDelta;
		RiskMetrics[2] = msg.minGamma;
		RiskMetrics[3] = msg.maxGamma;
		RiskMetrics[4] = msg.minVega;
		RiskMetrics[5] = msg.maxVega;
		//log("our risk metrics are min delta:" + msg.minDelta + " and max delta: " + msg.maxDelta);
		//log("our risk metrics are min gamma:" + msg.minGamma + " and max gamma: " + msg.maxGamma);
		//log("our risk metrics are min vega:" + msg.minVega + " and max vega: " + msg.maxVega);
	}

	public void newForecastMessage(ForecastMessage msg) {
		//Initialize forcast metrics 
		
		//log("I received a forecast message!");
		ForcastDelta = msg.delta;
		ForcastGamma = msg.gamma;
		ForcastVega = msg.vega; 
		
		//log("our forcast is: " + ForcastDelta + " Delta");
		//log("our forcast is: " + ForcastGamma+ " Gamma");
		//log("our forcast is: " + ForcastVega + " Vega");	
	}
	
	public void newVolUpdate(VolUpdate msg) {
		//Set the volatility  - multiply by 100 in the porfolio class 
		p.SetVol(msg.impliedVol);
		//log("the vol came in: " + msg.impliedVol);
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		//log("!!!!!!!!!!!!!!!!!!!!!!!!Penalty called...oh no!");
		//log("the security: " + idSymbol + " by: " + quantity);
		
		//log("my p delta is: " + p.PortfolioDelta());
		//log("my p gamma is: " + p.PortfolioGamma());
		//log("my p vega is: " + p.PortfolioVega());
		
		log("penalty, option=" + idSymbol);
		p.AddPosition(idSymbol, quantity);
		//log("my p delta is: " + p.PortfolioDelta());
		//log("my p gamma is: " + p.PortfolioGamma());
		//log("my p vega is: " + p.PortfolioVega());
		
				
		
	}

	@Override
	public void onSignal(ProcessPenaltyRequest msg) {
		super.onSignal(msg);
		log(" ------------------------------------------ ");
		double portfolioVega = 0;
		for (String option : p.data()) {
			double time = p.getTime(option);
			//log("time=" + time + " num=" + time/365.0);
			//log("system-may=" + daysToMayExp + " num=" + daysToMayExp/365.0);
			//log("system-jun=" + daysToJuneExp + " num=" + daysToJuneExp/365.0);
			
			double vega = Optionsutil.calculateVega(p.getSpot(), p.getStrike(option), (time/365.0),  0.01, p.getVol());
			InstrumentDetails details = instruments().getInstrumentDetails(option);
			log(details.expiration.toString());
			double systemTime = (details.expiration.toString().contains("May")) ? daysToMayExp : daysToJuneExp;
			double systemVega = Optionsutil.calculateVega(currentUnderlying, p.getStrike(option), (systemTime/365.0),  0.01, currentVol);
			double totalVega = vega * p.getAmount(option);
			PositionInfo info = calculatePNL();
			log("input spot=" + p.getSpot() + ", strike=" + p.getStrike(option) + ", vol=" + p.getVol() + ", time=" + time);
			log("system input, spot=" + currentUnderlying + ", vol=" + currentVol + ", time=" + systemTime);
			log(option + ", my vega=" + vega + ", system vega=" + systemVega);
			log(option + ", my positions=" + p.getAmount(option) + ", system positions=" + positionMap.get(option));
			portfolioVega += totalVega;
		}
		PortfolioRisk risk = calculateRisk();
		log("my vega=" + portfolioVega + ", system vega=" + risk.vega);
		
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		//log("Placing orders");
		
		//Agressiveness Value
		double AV = 1;
		
		//I change the forecast if it is below or above my risk limit 
		if(ForcastDelta < 0 && RiskMetrics[0] >  ForcastDelta) ForcastDelta = RiskMetrics[0];
		if(ForcastDelta >= 0 && RiskMetrics[1] < ForcastDelta) ForcastDelta = RiskMetrics[1];
		if(ForcastGamma < 0 && RiskMetrics[2] > ForcastGamma) ForcastGamma = RiskMetrics[2];
		if(ForcastGamma >= 0 && RiskMetrics[3] < ForcastGamma) ForcastGamma = RiskMetrics[3];
		if(ForcastVega < 0 && RiskMetrics[4] > ForcastVega) ForcastVega = RiskMetrics[4];
		if(ForcastVega >= 0 && RiskMetrics[5] < ForcastVega) ForcastVega = RiskMetrics[5];
		
		//my forecast vector using Agresiveness Value * (forecast - portfolio value)
		double[] forecast = new double[]{AV*(ForcastVega - p.PortfolioVega()),AV*(ForcastGamma - p.PortfolioGamma())};
		
		//Initialize my triple array 
		double[][][] Options = new double[10][10][2];
		Matrix B = new Matrix(forecast,2);
		double[] Mispricing = new double[10];
		
		for(int i = 0; i < p.data().size(); i++){
			//log(p.data().get(i));
		}
		for(int i = 0; i < 10; i ++){
			String xi = p.data().get(i);
			for(int y = 0; y < 10; y ++){
				String xy = p.data().get(y);
				if(i!=y){
					double[][] greeks = new double[][]{{p.Vega(xi),p.Vega(xy)},{p.Gamma(xi),p.Gamma(xy)}};
					Matrix A = new Matrix(greeks);
					Matrix x = A.solve(B);
				
					Options[i][y][0] = (int)((x.get(0, 0)));
					Options[i][y][1] = (int)((x.get(1,0)));
					//log("amount 1 is: " + (int)((x.get(0, 0))));
					//log("amount 2 is: " + (int)((x.get(1,0))));
					//log("the projected pgamma is: " + (p.PortfolioGamma() + (int)((x.get(0, 0)))* p.Gamma(xi) + (int)((x.get(1,0)))* p.Gamma(xy)));
					//log("the projected pvega is: " + (p.PortfolioVega() + (int)((x.get(0, 0)))* p.Vega(xi) + (int)((x.get(1,0)))* p.Vega(xy)));
					//log("the security i is: " + p.data().get(i) + " security y is " + p.data().get(y));
				} else {Options[i][y][0] = 1; Options[i][y][1] = 1;}
					
				////log("for " + xi + " and " + xy + " the values are: " + Options[i][y][0] +" and " + Options[i][y][1]);
			}
			Mispricing[i] = (p.Call(xi) - p.getPrice(xi))/p.getPrice(xi);
		}
		
		for(int i = 0; i < 10; i ++){
			for(int y = 0; y < 10; y ++){
				//log("the securities1 : " + p.data().get(i));
				//log("the securitiy2 is : " + p.data().get(y));
				//log(String.valueOf(Options[i][y][0]));
				//log(String.valueOf(Options[i][y][1]));
			}
		}
		// 0 is xi 1 is xy
		int[] index = new int[10];
		index = p.Sort(Mispricing);
		
		for(double x: Mispricing){
			//log("the mispricing is: " + x);
		}
		
		double amounti = 0;
		double amounty = 0;
		String Si = "";
		String Sy = ""; 
		int ini = 0;
		int iny = 0;
		
		outerloop:
		for(int i = 0; i < 10; i++){
			for(int y = 0; y < 10; y++){
				amounti = Options[index[i]][index[y]][0];
				amounty = Options[index[i]][index[y]][1];
				if(!p.p(Options[index[i]][index[y]][0], Mispricing[index[i]]) && !p.p(Options[index[i]][index[y]][1], Mispricing[index[y]]) && y!=i && amounti > -500 && amounti < 500
						&& amounty < 500 && amounty > -500){
					Si = p.data().get(index[i]);
					Sy = p.data().get(index[y]);
					//log(Si);
					//log(Sy);
					ini = index[i];
					iny = index[y];
					//log("i is " + i);
					//log("y is " + y);
					//log("the amout is: " + amounti);
					//log("the amount is " + amounty);
					break outerloop;
				}	
			}
		}
		//log("the projected pgamma in matching is: " + (p.PortfolioGamma() + amounti* p.Gamma(Si) + amounty* p.Gamma(Sy)));
		//log("the projected pvega in matching is: " + (p.PortfolioVega() + amounti* p.Vega(Si) + amounty* p.Vega(Sy)));
	
		
		//log("the securities are: " + Si + " and " + Sy);
		//log("the amounts are: " + amounti + " and " + amounty);
		//log("they are mispriced by: " + Mispricing[ini] + " and " + Mispricing[iny]);
		
		//log("the projected pgamma is: " + (p.PortfolioGamma() + amounti* p.Gamma(Si) + amounty* p.Gamma(Sy)));
		//log("the projected pvega is: " + (p.PortfolioVega() + amounti* p.Vega(Si) + amounty* p.Vega(Sy)));
	
		for(String x: p.positions.keySet()){
			//log("i have: " + p.getAmount(x) + " of " + x);
			//log("the gamma is: " + p.Gamma(x));
			//log("the vega is: " + p.Vega(x));
		}
		
		//log("my portfolio delta is: " + p.PortfolioDelta());
		//log("my portfolio gamma is: " + p.PortfolioGamma());
		//log("my portfolio vega is: " + p.PortfolioVega());
		
		OrderInfo[] orders = new OrderInfo[3];
		if(amounti > 0){
			orders[0] = new OrderInfo(Si, OrderSide.BUY, p.getAsk(Si),(int)amounti);
		}
		else {
			orders[0] = new OrderInfo(Si, OrderSide.SELL, p.getBid(Si),-(int)amounti);
		}
		if (amounty > 0){
			orders[1] = new OrderInfo(Sy, OrderSide.BUY, p.getAsk(Sy),(int)amounty);
		}
		else {
			orders[1] = new OrderInfo(Sy, OrderSide.SELL, p.getBid(Sy),-(int)amounty);
		}
		

		//log("my portfolio delta is: " + p.PortfolioDelta());
		//log("my portfolio gamma is: " + p.PortfolioGamma());
		//log("my portfolio vega is: " + p.PortfolioVega());
		
		
		double Dposition = (ForcastDelta - p.PortfolioDelta());
		
		//log("my delta amout to buy/sell is: " + Dposition);
		if(Dposition > 0){
			orders[2] = new OrderInfo(Underline, OrderSide.BUY, p.getAsk(Underline), (int)Dposition);
		} else{
			orders[2] = new OrderInfo(Underline, OrderSide.SELL, p.getBid(Underline), -(int)Dposition);
		}
		
		
		//log("my portfolio delta is: " + p.PortfolioDelta());
		//log("my portfolio gamma is: " + p.PortfolioGamma());
		//log("my portfolio vega is: " + p.PortfolioVega());
		//log("my pnl is: " + p.Pnl());
		//-----------------------------------------------------------------------------------------
		//-----Liquidating all positions ------------------------------------------------------------
		//----------------------------------------------------------------------------------------p
		
		/*
		Boolean liquidation = false;
		OrderInfo[] orders1 = new OrderInfo[p.positions.size()];
		if(volcount != p.getVol() && p.positions.size() > 0){
			int f = 0; 
			for(String x: p.keyset()){
				if(p.getAmount(x) > 0){
					orders1[f] = new OrderInfo(x,OrderSide.SELL, p.getPrice(x),(int)p.getAmount(x));
					f++;
				} else if(p.getAmount(x) < 0){
					orders1[f] = new OrderInfo(x,OrderSide.BUY, p.getPrice(x),(int)-p.getAmount(x));
					f++;
				}
			}
			volcount = p.getVol();
			p.LiquidatePositions();
			liquidation = true;
		}
		//-------------------------------------------------------------------------------------------
		//--------------------------------------------------------------------------------------------
		
		String[] BuySell = new String[6];
		BuySell[0] = p.ATM().get(0);
		BuySell[1] = p.ATM().get(1);
		BuySell[2] = p.ITM().get(0);
		BuySell[3] = p.ITM().get(1);
		BuySell[4] = p.OTM().get(p.OTM().size()-1);
		BuySell[5] = p.OTM().get(p.OTM().size()-2);
	
		//-------------------------------------------------------------------------------------------
		//-------------------------------------------------------------------------------------------
		
		
		double ITMratioJune = p.Gamma(BuySell[1])/p.Gamma(BuySell[3]);
		double OTMratioJune = p.Gamma(BuySell[1])/p.Gamma(BuySell[5]);
		int amount = 0;
		double v = 1.2;
		int count = 0;
		Boolean loop = true;
		double ITMratioMay = 0;
		double OTMratioMay = 0, PVega = 0, PGamma = 0,atmvega = 0,atmgamma = 0, itmvega = 0, itmgamma = 0, otmvega  = 0, otmgamma = 0;
		double agg = .9;
		while (loop){
			
			ITMratioMay = p.Gamma(BuySell[0])/2/p.Gamma(BuySell[2])*v;
			OTMratioMay = p.Gamma(BuySell[0])/p.Gamma(BuySell[4])*v;
			
			amount = (int)(((ForcastVega - p.PortfolioVega())/2)/(p.Vega(BuySell[0]) - ITMratioMay*p.Vega(BuySell[2]) - OTMratioMay*p.Vega(BuySell[4])));
			
			PVega = amount*(p.Vega(BuySell[0]) - ITMratioMay * p.Vega(BuySell[2]) - OTMratioMay * p.Vega(BuySell[4]));
			PGamma = amount*(p.Gamma(BuySell[0]) - ITMratioMay *p.Gamma(BuySell[2]) - OTMratioMay * p.Gamma(BuySell[4]));
			
			atmvega = p.Vega(BuySell[0])*amount;
			atmgamma = p.Gamma(BuySell[0])*amount;
			itmvega = ITMratioMay * amount * p.Vega(BuySell[2]);
			itmgamma = ITMratioMay * amount * p.Gamma(BuySell[2]);
			otmvega = OTMratioMay * amount * p.Vega(BuySell[4]);
			otmgamma = OTMratioMay * amount * p.Gamma(BuySell[4]);
			
			if(PVega > agg*RiskMetrics[4] && PVega < agg*RiskMetrics[5] && PGamma > agg*RiskMetrics[2] && PGamma < agg*RiskMetrics[3] &&
			amount < 0 && atmvega > agg*RiskMetrics[4] && atmgamma > agg*RiskMetrics[2] && itmvega > agg*RiskMetrics[4] && itmgamma >agg*RiskMetrics[2])
				loop = false; 
			
			v = v - .01;
			
			count++;
			if(count == 100)
				break;
			
		}

		//log("The amount is :" + amount);
		//log("The projected portfolio gamma is: " +PGamma);
		//log("The projected portfolio vega is: " + PVega);

		OrderInfo[] orders = new OrderInfo[4];
		 
		
		
		orders[0] = new OrderInfo(BuySell[0],OrderSide.SELL, p.getPrice(BuySell[0]),-amount);
		p.AddPosition(BuySell[0], amount);
		orders[1] = new OrderInfo(BuySell[4],OrderSide.BUY, p.getPrice(BuySell[4]),-(int)(amount*OTMratioMay));
		p.AddPosition(BuySell[4], -(amount*OTMratioMay));
		orders[2] = new OrderInfo(BuySell[2],OrderSide.BUY, p.getPrice(BuySell[2]),-(int)(amount*ITMratioJune));
		p.AddPosition(BuySell[2], -(amount*ITMratioMay));
		
		
		
		//------------------------------------------------
		//Delta Hedge 
		//-------------------------------------------------
		
		if(p.HedgeDelta() > 0){
			orders[3] = new OrderInfo(Underline, OrderSide.BUY, p.getSpot(),p.HedgeDelta());
		}else if(p.HedgeDelta() <= 0){
			orders[3] = new OrderInfo(Underline, OrderSide.SELL, p.getSpot(),p.HedgeDelta());	
		}
			
		p.AddPosition(Underline, p.HedgeDelta());
		
		for(String x: p.positions.keySet())
		{
			//log("I have " + p.getAmount(x) + " of " + x);
			//log("The delta is: " + p.getAmount(x) * p.Delta(x));
			//log("The gamma is: " + p.getAmount(x) * p.Gamma(x));
			//log("The vega is: " + p.getAmount(x) * p.Vega(x));
		}
		//log("my p delta is: " + p.PortfolioDelta());
		//log("my p gamma is: " + p.PortfolioGamma());
		//log("my p vega is: " + p.PortfolioVega());
		//log("my pnl is: " + p.Pnl());
		
		
		
		OrderInfo[] all = new OrderInfo[orders.length + orders1.length];
		if(liquidation){
			System.arraycopy(orders1,0,all,0 ,orders1.length);
			System.arraycopy(orders, 0, all, orders1.length, orders.length);
			return all;
		}else{	
		*/
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		p.AddPosition(idSymbol, quantity);
		//log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}


// Portfolio class everything goes through thia 
class Portfolio {
	public double PortfolioPnL;
	//holds the positions [bid , ask, amount] 
	public LinkedHashMap<String, double[]> positions = new LinkedHashMap<String, double[]>();
	//private HashMap<String, double[]> positions = new HashMap<String, double[]>();
	
	//holds the data for all the securities [bid, ask, strike, time]
	public LinkedHashMap<String,double[]> Data = new LinkedHashMap<String, double[]>(); 
	//private HashMap<String,double[]> Data = new HashMap<String, double[]>(); 
	
	double spot = 100.0; 
	//initialize vol
	private double vol = 0.4;
	//interest rates are set at 0.01 
	private double r = 0.01;
	private double days = 365.0;
	
	//values [bid,ask, amount]
	public Portfolio(){}
	
	
	// values = [spot, amount]
	public void AddPosition(String ticker, double values){
		double nAmount = values;
		double[] nValues = new double[2];
		if(positions.containsKey(ticker)){
			double pAmount = positions.get(ticker)[1];
			double pPrice = positions.get(ticker)[0];
			
			nValues[0] = ((pPrice * pAmount) + (this.getPrice(ticker) * nAmount))/(pAmount + nAmount);
			nValues[1] = pAmount + nAmount; 
			positions.put(ticker,nValues); 
		} 
		else{
			double[] values1 = new double[2];
			values1[0] = this.getPrice(ticker);
			values1[1] = values;
			positions.put(ticker, values1);
		}
	}
	
	//values = [bid, ask, strike, time]

	public void UpdatePrice(String ticker, double[] values){
		Data.put(ticker, values);
	}
	
	public double Call(String ticker){
		if(Data.containsKey(ticker)){
			return Optionsutil.Call(spot, Data.get(ticker)[2], Data.get(ticker)[3]/days, r, vol);
		} else return 0;
	}
	
	
	public double Delta(String ticker){
		if(Data.containsKey(ticker)){
			InstrumentDetails details = instruments().getInstrumentDetails(ticker);
			if(details.type.isOption()){
				double strike = Data.get(ticker)[2];
				double time = Data.get(ticker)[3];
				double delta = Optionsutil.calculateDelta(spot, strike, time/days, r, vol);
				return delta;	
			} else return 1; 
		} else return 0;
	}
	
	public double Gamma(String ticker){
		if(Data.containsKey(ticker)){
			InstrumentDetails details = instruments().getInstrumentDetails(ticker);
			if(details.type.isOption()){
				double strike = Data.get(ticker)[2];
				double time = Data.get(ticker)[3];
				double gamma = Optionsutil.calculateGamma(spot, strike, time/days, r, vol);
				return this.round(gamma,4);
			} else return 0;
		} else return 0;
	}
	
	public double Vega(String ticker){
		if(Data.containsKey(ticker)){
			InstrumentDetails details = instruments().getInstrumentDetails(ticker);
			if(details.type.isOption()){
				double strike = Data.get(ticker)[2];
				double time = Data.get(ticker)[3];
				//log("time=" + time + " num=" + time/days);
				//log("system-may=" + daysToMayExp + " num=" + daysToMayExp/days);
				//log("system-jun=" + daysToJuneExp + " num=" + daysToJuneExp/days);
				double vega = Optionsutil.calculateVega(spot, strike, time/days, r, vol);
				return this.round(vega,4);
			} else return 0;
		} else return 0;
	}
	
	public double PortfolioDelta(){
		double pdelta = 0;
		for(String key: positions.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption())
				pdelta += this.Delta(key) * positions.get(key)[1];
			else pdelta += positions.get(key)[1];
		}
		return pdelta; 
	}
	
	public double PortfolioGamma(){
		double pgamma = 0; 
		for(String key: positions.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption())
				pgamma += this.Gamma(key) * positions.get(key)[1];
		}
		return pgamma; 
	}
	
	public double PortfolioVega(){
		double pvega = 0;
		for(String key: positions.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption())
				pvega += this.Vega(key) * positions.get(key)[1];
		}
		return pvega; 
	}
	

	public double PositionPnL(String ticker){
		if(positions.containsKey(ticker)){
			double Cprice = (this.getPrice(ticker));
			double Bprice = positions.get(ticker)[0];
			int amount = (int)positions.get(ticker)[1];
			return (Cprice - Bprice) * (int)amount;}
		else return 0;
	}
	
	public double Pnl(){
		double P = 0;
		for(String key: positions.keySet()){
			P += PositionPnL(key);
		}
		return P; 
	}
	
	public double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}
	
	public Boolean p(double x,double y){
		if(x > 0 && y >0) return true;
		if(x < 0 && y <0) return true;
		if(x < 0 && y >0) return false;
		if(x > 0 && y < 0)return false; 
		else return false; 
	}

/*
	public ArrayList<String> ATM(){
		//values = [bid, ask, strike, time]

		ArrayList<String> options = new ArrayList<String>(0);
		
		for(String key: Data.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption() && Math.abs(spot - Data.get(key)[2]) < 5.00){
				options.add(key);
			}	
		}
		 
		this.OptionSort(options);
		if(options.size() == 0){
			options.add(HundredMay);
			options.add(HundredJune);
		}
		return options; 
	}
	
	public ArrayList<String> ITM(){
		
		ArrayList<String> options = new ArrayList<String>();
		int i = 0;
		for(String key: Data.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption() && Math.abs(spot - Data.get(key)[2]) > 5 && spot > Data.get(key)[2]){
				options.add(key);
				i++;
			}	
		}
		this.OptionSort(options);
		return options; 
	}
	
	public ArrayList<String> OTM(){
		ArrayList<String> options = new ArrayList<String>();
		int i = 0;
		for(String key: Data.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(key);
			if(details.type.isOption() && Math.abs(spot - Data.get(key)[2]) > 5 && spot < Data.get(key)[2]){
				options.add(key);
				i++;
			}	
		}
		this.OptionSort(options);
		return options; 
	}
	
*/
	public int[] Sort(double[] num1)
	{
		 double[] num = num1.clone();
	     int j;
	     boolean flag = true;   // set flag to true to begin first pass
	     double temp;
	     int temp1;
	     //holding variable
	     int[] index = {0,1,2,3,4,5,6,7,8,9}; 
	     while ( flag )
	     {
	            flag= false;    //set flag to false awaiting a possible swap
	            for( j=0;  j < num.length -1;  j++ )
	            {
	                   if ( Math.abs(num[ j ]) < Math.abs(num[j+1]) )   // change to > for ascending sort
	                   {
	                           temp = num[ j ];                //swap elements
	                           num[ j ] = num[ j+1 ];
	                           num[ j+1 ] = temp;
	                           
	                           temp1 = index[j];
	                           index[j] = index[j+1];
	                           index[j+1] = temp1;
	                           flag = true;              //shows a swap occurred  
	                  } 
	            } 
	      }
	     return index; 
	} 
	
	public void OptionsSort(ArrayList<String> options)
	{
	     int j;
	     boolean flag = true;   // set flag to true to begin first pass
	     String temp;
	     while ( flag )
	     {
	            flag= false;    //set flag to false awaiting a possible swap
	            for( j=0;  j < options.size() -1;  j++ )
	            {
	                   if (Data.get(options.get(j))[2] >= Data.get(options.get(j+1))[2])   // change to > for ascending sort
	                   {
	                	   if((Data.get(options.get(j))[2] == Data.get(options.get(j+1))[2])){
	                		   if((Data.get(options.get(j))[3] == Data.get(options.get(j+1))[3])){
	                			   temp = options.get(j);
	                			   options.set(j, options.get(j+1));
	                			   options.set(j+1, temp);
	                			   flag = true;
	                		   }
	                	   } else{
	                		   temp = options.get(j);
                			   options.set(j, options.get(j+1));
                			   options.set(j+1, temp);
                			   flag = true;	
	                	   }
	                   } 
	           } 
	     } 
	} 
	
	public int HedgeDelta(){return (int) -this.PortfolioDelta();}
	public void SetVol(double volatility){vol = volatility ;}
	public double getVol(){return vol;}
	
	public double getPrice(String ticker){return (Data.get(ticker)[0] + Data.get(ticker)[1])/2.0;}
	public double getStrike(String ticker){return Data.get(ticker)[2];}
	public double getTime(String ticker) {return Data.get(ticker)[3];}
	public void SetSpot(double Spot){spot = Spot;}
	public double getSpot(){return spot;}
	
	public double getAmount(String ticker){
		if(positions.containsKey(ticker))
			return positions.get(ticker)[1];
		else return 0;
	}
	
	public String[] keyset(){
		String[] a = p.positions.keySet().toArray(new String[p.positions.size()]);
		return a;
	}

	public double getPnL(){return PortfolioPnL;}
	public void LiquidatePositions(){
		PortfolioPnL += this.Pnl();
		positions.clear();
	}
	
	public ArrayList<String> data(){
		ArrayList<String> options = new ArrayList<String>(0);
		for(String x: Data.keySet()){
			InstrumentDetails details = instruments().getInstrumentDetails(x);
			if(details.type.isOption()) 
				options.add(x);
		}
		
		OptionsSort(options);
		return options; 
	}
	public double getBid(String ticker){
		return Data.get(ticker)[0];
	}
	
	public double getAsk(String ticker){
		return Data.get(ticker)[1];
	}
	
}}
