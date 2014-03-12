

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
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



public class BAR1Options extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private HashMap<String,Double> time = new HashMap<String,Double>();
	private Portfolio p = new Portfolio();
	private double ForcastDelta = 0, ForcastGamma = 0, ForcastVega = 0; 
	public String Underline;
	double AVdown;
	double AVup;
	
	double MinDelta = 0, MaxDelta = 0, MinGamma = 0, MaxGamma = 0, MinVega = 0, MaxVega = 0; 
	//riskmetrics = [mindelta,maxdelta, mingamma, maxgamma, minvega, maxvega]
	
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		setup.addVariable("AVdown", "Agressiveness factor down", "double", ".98");
		setup.addVariable("AVup", "Agressiveness factor up", "double", "1.02");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database; 
		AVdown = getDoubleVar("AVdown");
		AVup = getDoubleVar("AVup");
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
		container.addProbe("time", "time", true);
	}	
	
	//values = [bid, ask, strike, time]
	public void newBidAsk(String idSymbol, double bid, double ask) {	
		
		log("new price of " + (bid+ask)/2 + " for " + idSymbol);
		//Get details on the instrument 
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		
		if(!details.type.isOption()) {
			p.SetSpot((bid+ask)/2);
			Underline = idSymbol;
		}
		
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
		log("I received an admin message!");
		MinDelta = msg.minDelta;
		MaxDelta = msg.maxDelta;
		MinGamma = msg.minGamma;
		MaxGamma = msg.maxGamma;
		MinVega = msg.minVega;
		MaxVega = msg.maxVega;
		log("our risk metrics are min delta:" + msg.minDelta + " and max delta: " + msg.maxDelta);
		log("our risk metrics are min gamma:" + msg.minGamma + " and max gamma: " + msg.maxGamma);
		log("our risk metrics are min vega:" + msg.minVega + " and max vega: " + msg.maxVega);
	}

	public void newForecastMessage(ForecastMessage msg) {
		//Initialize forcast metrics 
		
		log("I received a forecast message!");
		ForcastDelta = msg.delta;
		ForcastGamma = msg.gamma;
		ForcastVega = msg.vega; 
		
		log("our forcast is: " + ForcastDelta + " Delta");
		log("our forcast is: " + ForcastGamma+ " Gamma");
		log("our forcast is: " + ForcastVega + " Vega");	
	}
	
	public void newVolUpdate(VolUpdate msg) {
		//Set the volatility  - multiply by 100 in the porfolio class 
		p.SetVol(msg.impliedVol);
		log("the vol came in: " + msg.impliedVol);
		container.getProbe("time").set(p.Pnl());
		
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("!!!!!!!!!!!!!!!!!!!!!!!!Penalty called...oh no!");
		log("the security: " + idSymbol + " by: " + quantity);
		double[] a = {price,quantity};
		
		log("my delta should be: " + p.PortfolioDelta());
    	log("my gamma should be: " + p.PortfolioGamma());
    	log("my vega should be: " + p.PortfolioVega());
    	log("my pnl should be: " + p.Pnl());
    	
		p.AddPosition(idSymbol, a);
	}

	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		log("Placing orders");
		double AV = 1;
		
		//I change the forecast if it is below or above my risk limit 
		if(ForcastDelta < 0 && MinDelta >  ForcastDelta) ForcastDelta = MinDelta;
		if(ForcastDelta >= 0 && MaxDelta < ForcastDelta) ForcastDelta = MaxDelta;
		if(ForcastGamma < 0 && MinGamma > ForcastGamma) ForcastGamma = MinGamma;
		if(ForcastGamma >= 0 && MaxGamma < ForcastGamma) ForcastGamma = MaxGamma;
		if(ForcastVega < 0 && MinVega > ForcastVega) ForcastVega = MinVega;
		if(ForcastVega >= 0 && MaxVega < ForcastVega) ForcastVega = MaxVega;
		
		
		if(ForcastVega <= 0 && p.PortfolioVega() < ForcastVega) AV = AVup;
		if(ForcastVega >= 0 && p.PortfolioVega() > ForcastVega) AV = AVup;
		if(ForcastVega <= 0 && p.PortfolioVega() > ForcastVega) AV = AVdown;
		if(ForcastVega >= 0 && p.PortfolioVega() < ForcastVega) AV = AVdown;
		
		log("the time is: " + time.get(Underline));
		log("FD: " + ForcastDelta + " FG: " + ForcastGamma + " FV: " + ForcastVega );
		
		//my forecast vector using Agresiveness Value * (forecast - portfolio value)
		double[] forecast = new double[]{AV*(ForcastVega - p.PortfolioVega()),AV*(ForcastGamma - p.PortfolioGamma()),AV*(ForcastDelta - p.PortfolioDelta())};
		
		//Initialize my triple array 
		double[][][][] Options = new double[10][10][10][3];
		Matrix B = new Matrix(forecast,3);
		double[] Mispricing = new double[10];
		
		for(int i = 0; i < 10; i ++){
			String xi = p.data().get(i);
			for(int y = 0; y < 10; y ++){
				String xy = p.data().get(y);
				for(int z = 0; z < 10; z++){
					String xz = p.data().get(z);
					if(i!=y && i!=z && z!=y){
						double[][] greeks = new double[][]{{p.round(p.Vega(xi),4),p.round(p.Vega(xy),4),p.round(p.Vega(xz),4)},
								{p.round(p.Gamma(xi),4),p.round(p.Gamma(xy),4),p.round(p.Gamma(xz),4)},{p.round(p.Delta(xi),4),
									p.round(p.Delta(xy),4),p.round(p.Delta(xz),4)}};
						Matrix A = new Matrix(greeks);
						double[] dummy = {1,1,1};
						Matrix x = new Matrix(dummy,3);
						if(A.det() != 0)
							x = A.solve(B);
						
						
						Options[i][y][z][0] = (int)((x.get(0, 0)));
						Options[i][y][z][1] = (int)((x.get(1,0)));
						Options[i][y][z][2] = (int)((x.get(2,0)));	
						
					} else {Options[i][y][z][0] = 1; Options[i][y][z][1] = 1; Options[i][y][z][2] = 1;}	
				}	
				//log("for " + xi + " and " + xy + " the values are: " + Options[i][y][0] +" and " + Options[i][y][1]);
			}
			Mispricing[i] = (p.Call(xi) - p.getPrice(xi))/p.getPrice(xi);
		}
		// 0 is xi 1 is xy
		int[] index = new int[10];
		index = p.Sort(Mispricing);
		
		double amounti = 0;
		double amounty = 0;
		double amountz = 0;
		String Si = "";
		String Sy = ""; 
		String Sz = "";
		
		ArrayList<Double> cost = new ArrayList<Double>();
		int[][] index1 = new int[1000][3];
	
		int count = 0;
		outerloop:
		for(int i = 0; i < 10; i++){
			for(int y = 0; y < 10; y++){
				for(int z = 0; z < 10; z++){
					amounti = Options[index[i]][index[y]][index[z]][0];
					amounty = Options[index[i]][index[y]][index[z]][1];
					amountz = Options[index[i]][index[y]][index[z]][2];
					
					if(i!= y && y!=z && i!=z && amounti != 1 && amounty != 1 && amountz!= 1 && p.p(amounti, Mispricing[index[i]]) &&
							amounty > -600 && amounty < 600 && amountz < 600 && amountz > -600 && 
							p.p(amounty, Mispricing[index[y]]) && p.p(amountz, Mispricing[index[z]]) && amounti > -600 && amounti < 600){
						
						Si = p.data().get(index[i]);
						Sy = p.data().get(index[y]);
						Sz = p.data().get(index[z]);
						
						cost.add(( -amounti*p.BidAsk(Si, (int)amounti) + -amounty*p.BidAsk(Sy, (int)amounty) + -amountz*p.BidAsk(Sz, (int)amountz)) + 
								( amounti*p.BidAsk(Si, -(int)amounti) + amounty*p.BidAsk(Sy, -(int)amounty) + amountz*p.BidAsk(Sz, -(int)amountz)));
						
						int[] indexes = new int[3];
						indexes[0] = index[i];
						indexes[1] = index[y];
						indexes[2] = index[z];
						index1[count] = indexes;
						count++;
					}
				}
			}	
		}	
		int min = 0;
		if(cost.size() != 0){
			min = p.getMaxValue(cost);
			//min = p.getMinValue(cost);
		}
		
		Si = p.data().get(index1[min][0]);
		Sy = p.data().get(index1[min][1]);
		Sz = p.data().get(index1[min][2]);
		amounti = Options[index1[min][0]][index1[min][1]][index1[min][2]][0];
		amounty = Options[index1[min][0]][index1[min][1]][index1[min][2]][1];
		amountz = Options[index1[min][0]][index1[min][1]][index1[min][2]][2];
		
		
		OrderInfo[] orders1 = new OrderInfo[p.positions.size()];
		OrderInfo[] orders = new OrderInfo[3];
		OrderInfo[] NoTrades = new OrderInfo[0];
		
		Boolean liquidation = false; 
		Boolean ZeroTrades = false; 		
		
		if(cost.size() == 0) {
			log("liquidation");
			log("there are no profitable opportunities");
			
		    int	lcount = 0;
		    if(p.GetPortfoliiSize() != 0){
				for(String x: p.keyset()){
					log(" liquidation securiy: " + x + " bid of: " + p.getBid(x) + " ask of: " + p.getAsk(x) + " amount of: " + p.getAmount(x));
					if(p.getAmount(x) > 0){orders1[lcount] = new OrderInfo(x,OrderSide.SELL, p.getBid(x),Math.abs((int)p.getAmount(x))); lcount++;}
					if(p.getAmount(x) < 0){orders1[lcount] = new OrderInfo(x,OrderSide.BUY, p.getAsk(x),Math.abs((int)p.getAmount(x))); lcount++;}
				}
				
				p.LiquidatePositions();
				log("p.getpnl = " + p.getPnL());
				liquidation = true;
		    } else {
		    	log("there are no profitable opportunities----------------");
		    	log("my delta should be: " + p.PortfolioDelta());
		    	log("my gamma should be: " + p.PortfolioGamma());
		    	log("my vega should be: " + p.PortfolioVega());
		    	log("my pnl should be: " + p.Pnl());
		    	ZeroTrades = true;
		    }
				
		} else{
				log("Si: " + Si + "     Sy: " + Sy + "     Sz: " + Sz);
				log("amounti: " + amounti +"     amounty: " + amounty + "    amountz: " + amountz);
				log("i: " + index1[min][0] + " y: " + index1[min][1] + " z: " + index1[min][2]);
				log("bidi: " + p.getBid(Si) + " aski: " + p.getAsk(Si) + " bidy: " + p.getBid(Sy) + " asky: " + p.getAsk(Sy) + " bidz: " + p.getBid(Sz) + " askz: " + p.getAsk(Sz));
			
				if(amounti > 0){orders[0] = new OrderInfo(Si, OrderSide.BUY, p.getAsk(Si),(int)amounti);}
				else {orders[0] = new OrderInfo(Si, OrderSide.SELL, p.getBid(Si),-(int)amounti);}
				
				if (amounty > 0){orders[1] = new OrderInfo(Sy, OrderSide.BUY, p.getAsk(Sy),(int)amounty);}
				else {orders[1] = new OrderInfo(Sy, OrderSide.SELL, p.getBid(Sy),-(int)amounty);}
				
				if (amountz > 0){orders[2] = new OrderInfo(Sz, OrderSide.BUY, p.getAsk(Sz),(int)amountz);}
				else {orders[2] = new OrderInfo(Sz, OrderSide.SELL, p.getBid(Sz),-(int)amountz);}
		}
		
		if(ZeroTrades) {return NoTrades;}
		else if(liquidation){return orders1;} 
		else {return orders;}
	}
	
	/*
	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}*/

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
		double[] a = {price, quantity};
		p.AddPosition(idSymbol,a);
		
		log("Delta: " + p.PortfolioDelta() + " ----Gamma: " + p.PortfolioGamma() + " -----Vega: " + p.PortfolioVega());
		log("Normal Pnl: " + p.Pnl() + "   ::Pnl after liquidation: " + p.getPnL());
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}


// Portfolio class everything goes through thia 
class Portfolio {
	private double PortfolioPnL = 0;
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
	public void AddPosition(String ticker, double[] values){
		double[] nValues = new double[2];
		if(positions.containsKey(ticker)){
			if(positions.get(ticker)[1] + values[1] != 0){
				double pAmount = positions.get(ticker)[1];
				double pPrice = positions.get(ticker)[0];
				
				nValues[0] = ((pPrice * pAmount) + (values[0] * values[1]))/(pAmount + values[1]);
				nValues[1] = pAmount + values[1]; 
				positions.put(ticker,nValues); 
			} else{
				positions.remove(ticker);
			} 
		} else{
			positions.put(ticker, values);
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
				return gamma;
			} else return 0;
		} else return 0;
	}
	
	public double Vega(String ticker){
		if(Data.containsKey(ticker)){
			InstrumentDetails details = instruments().getInstrumentDetails(ticker);
			if(details.type.isOption()){
				double strike = Data.get(ticker)[2];
				double time = Data.get(ticker)[3];
				double vega = Optionsutil.calculateVega(spot, strike, time/days, r, vol);
				return vega;
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
			int amount = (int)positions.get(ticker)[1];
			double Cprice = this.BidAsk(ticker, -amount);
			double Bprice = positions.get(ticker)[0];
			return (Cprice - Bprice) * (int)amount;}
		else return 0;
	}
	
	public double Pnl(){
		double P = 0;
		for(String key: positions.keySet()){
			P += PositionPnL(key);
		}
		P += this.getPnL();
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
		else return true; 
	}

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
		String[] a = {""};
		if(p.positions.size()!= 0)
			a = p.positions.keySet().toArray(new String[p.positions.size()]);
		return a;
	}

	public double getPnL(){return PortfolioPnL;}
	public void LiquidatePositions(){
		PortfolioPnL = Pnl();
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
		if(Data.containsKey(ticker))
			return Data.get(ticker)[0];
		else return 0;
	}
	
	public double getAsk(String ticker){
		if(Data.containsKey(ticker))
			return Data.get(ticker)[1];
		else return 0;
		
	}
	
	public  double BidAsk(String ticker, int amount){
		double value = 0;
		if(amount > 0){
			value =  this.getAsk(ticker);
		}
		else if(amount <= 0){
			value = this.getBid(ticker);
		}
		return value;
	}
	
	public int getMinValue(ArrayList<Double> numbers){  
		  double minValue = (numbers.get(0));
		  int minindex = 1;
		  for(int i=1;i<numbers.size();i++){  
		    if((numbers.get(i)) < minValue){  
		      minValue = (numbers.get(i));
		      minindex = i;
		    }  
		  }  
		  return minindex;  
	}  
	
	public int getMaxValue(ArrayList<Double> numbers){  
		  double maxValue = (numbers.get(0));
		  int maxindex = 0;
		  for(int i=1;i<numbers.size();i++){  
		    if((numbers.get(i)) > maxValue){  
		      maxValue = (numbers.get(i));
		      maxindex = i;
		    }  
		  }  
		  return maxindex;  
	}  
	
	public int GetPortfoliiSize(){
		return positions.size();
	}
}}

