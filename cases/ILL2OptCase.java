import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.chicago.cases.AbstractOptionsCase;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.options.OrderInfo.OrderSide;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class ILL2OptCase extends AbstractOptionsCase implements OptionsCase {

	/**
	 * @param args
	 */
	
	double gamma = -1;
	double delta = -1;
	double vega = -1;
	
	double cash = 0;
	
	double dg;
	double dd;
	double dv;
	
	_Gaussian Gaussian = new _Gaussian();
	
	double minGamma;
	double maxGamma;
	double minDelta;
	double maxDelta;
	double minVega;
	double maxVega;
	
	double impliedVol = 0;
	double vol_change;
	
	double time = 0;
	
	double T = 100;
	
	double r = 1.01;
	
	private IDB myDatabase;
	int factor;
	
	private List<String> knownSymbols = new ArrayList<String>();
	private List<String> knownUSymbols = new ArrayList<String>();
	
	private HashMap<String, Double> ask_map = new HashMap<String, Double>();
	private HashMap<String, Double> bid_map = new HashMap<String, Double>();
	
	private HashMap<String, Double> u_ask_map = new HashMap<String, Double>();
	private HashMap<String, Double> u_bid_map = new HashMap<String, Double>();
	
	private HashMap<String, Integer> owned_options = new HashMap<String, Integer>();
	private HashMap<String, Integer> owned_underlying = new HashMap<String, Integer>();
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void addVariables(IJobSetup setup) {
		// TODO Auto-generated method stub
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}
	
	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");
		
		boolean first = true;
		
		if(gamma != -1){
			first = false;
			dg = msg.gamma - gamma;
			dd = msg.delta - delta;
			dv = msg.vega - vega;
		}
		
		gamma = msg.gamma;
		delta = msg.delta;
		vega = msg.vega;
		
		if(first)
			return;
		
		
	}
	
	public void initializeAlgo(IDB database) {
		log("starting");

		
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
		
	
	}
	
	public void newRiskMessage(RiskMessage msg) {
		log("I received an admin message!");
		minGamma = msg.minGamma;
		maxGamma = msg.maxGamma;
		minDelta = msg.minDelta;
		maxDelta = msg.maxDelta;
		minVega = msg.minVega;
		maxVega = msg.maxVega;
	}
	
	public void newVolUpdate(VolUpdate msg) {
		log("I received a vol update message!");
		time++;
		impliedVol = msg.impliedVol;
	}
	
	public void newBidAsk(String idSymbol, double bid, double ask) {
		
		log("New BidAsk");
		
		if(idSymbol.length() < 14){
			if(!knownUSymbols.contains(idSymbol)){
				knownUSymbols.add(idSymbol);
				owned_underlying.put(idSymbol, 0);
			}
			u_ask_map.put(idSymbol, ask);
			u_bid_map.put(idSymbol, bid);
		} else {
			ask_map.put(idSymbol, ask);
			bid_map.put(idSymbol, bid);
			if(!knownSymbols.contains(idSymbol)){
				knownSymbols.add(idSymbol);
				owned_options.put(idSymbol, 0);
			}
		}
		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
	}
	
	private double price_option(String symbol, double S, double K){
		double r = 1.01;
		
		if(symbol.toCharArray()[16] == '5')
			T = 100;
		else
			T = 130;
		
		double d1 = (Math.log(S/K)+(r+impliedVol*impliedVol/2)*(T-time)) / (impliedVol*Math.sqrt(T-time)); 
		double d2 = d1 - impliedVol*Math.sqrt(T-time);
		
		double price = Gaussian.phi(d1) * S - Gaussian.phi(d2) * K * Math.exp(-1*r*(T-time));
		
		return price;
	}
	
	private double solve_quadratic(double a, double b, double c)
	{
		double temp = -1 * b + Math.sqrt(b*b - 4*a*c);
		return temp/(2*a);
	}
	

	
	private double compute_gamma(double underlying, double strike)
	{
		double S = underlying;
		double K = strike;
		double d1 =  (Math.log(S/K)+(r+impliedVol*impliedVol/2)*(T-time)) / (impliedVol*Math.sqrt(T-time));
		return Gaussian.phi(d1)/(S*impliedVol*Math.sqrt(T-time));
	}
	
	private double compute_vega(double underlying, double strike)
	{
		double S = underlying;
		double K = strike;
		double d1 =  (Math.log(S/K)+(r+impliedVol*impliedVol/2)*(T-time)) / (impliedVol*Math.sqrt(T-time));
		return Gaussian.phi(d1)*S*Math.sqrt(T-time);
	}
	
	private double compute_delta(double underlying, double strike)
	{
		double S = underlying;
		double K = strike;
		double d1 =  (Math.log(S/K) + (r+impliedVol*impliedVol/2)*(T-time)) / (impliedVol*Math.sqrt(T-time));
		return Gaussian.phi(d1);
	}

	private int find_best_sell(ArrayList<Double> greeks_diff, ArrayList<Double[]> price, double bid){
		
		double min = 9999;
		int max_ind = -1;
		double cur;
		double greeks;
		
		for(int i=1; i < greeks_diff.size(); i+=2){
			greeks = greeks_diff.get(i);
			if(min > greeks && greeks != -1){
				min = greeks;
				max_ind = i;
			}
		}
		
		return max_ind;
		
	}
	
	private int find_best_buy(ArrayList<Double> greeks_diff, ArrayList<Double[]> price, double ask){
		
		double min = 9999;
		int max_ind = -1;
		double cur;
		double greeks;
		
		for(int i=0; i < greeks_diff.size(); i+= 2){
			greeks = greeks_diff.get(i);
			if(min > greeks && greeks != -1){
				min = greeks;
				max_ind = i;
			}
		}

		return max_ind;
		
	}

	
	public OrderInfo[] placeOrders() {
		// Place a buy order of 100.00 with qty of 10 for every symbol we know of
		// Note: Just a 'dummy' implementation.
		log("Placing orders");
		int y = 0;
		//OrderInfo[] orders = new OrderInfo[knownSymbols.size()];
		ArrayList<OrderInfo> orders = new ArrayList<OrderInfo>();
		
		for(int z = 0; z < knownUSymbols.size(); z++){

			String Usymbol = knownUSymbols.get(z);
			double u_ask = u_ask_map.get(Usymbol); 
			double u_bid = u_bid_map.get(Usymbol); 
			
			for (int i = 0; i < knownSymbols.size(); i++) {
				
				String symbol = knownSymbols.get(i); 
				double ask = ask_map.get(symbol); 
				double bid = bid_map.get(symbol);
				
				ArrayList<Double> greeks_diff = new ArrayList<Double>();
				ArrayList<Double[]> price = new ArrayList<Double[]>();
	
				//for (double strike = 80; strike <= 120; strike += 10){
					double strike = Double.parseDouble(symbol.substring(symbol.lastIndexOf("-")).replace("C", "").replace("-", ""));
					
					double u_sell = u_ask + 2;
					double u_buy = u_bid + 2;
					
					price.add(new Double[] {price_option(symbol, u_sell, strike), u_sell, strike});
					//price.add(new Double[] {price_option(symbol, u_ask/2+u_bid/2, strike), u_ask/2+u_bid/2, strike});
					price.add(new Double[] {price_option(symbol, u_buy, strike), u_buy, strike});
					
					log("----RESULTS---");
					log(Double.toString(price_option(symbol, u_buy, strike)));
					log(Double.toString(u_buy));
					log(Double.toString(strike));
					//price.add(new Double[] {price_option(symbol, u_ask*(2/3)+u_bid/3, strike), u_ask*(2/3)+u_bid/3, strike});
					//price.add(new Double[] {price_option(symbol, u_bid*(2/3)+u_ask/3, strike), u_bid*(2/3)+u_ask/3, strike});
				//}
				
				for(int j=0; j < 2; j++){
					double g = compute_gamma(price.get(j)[1], price.get(j)[2]);
					double v = compute_vega(price.get(j)[1], price.get(j)[2]);
					double d = compute_delta(price.get(j)[1], price.get(j)[2]);
					if((maxGamma-g) < 0 || (g-minGamma) < 0 || (maxVega-v) < 0 || (v-minVega) < 0 || (maxDelta-d) < 0 || (d-minDelta) < 0)
						greeks_diff.add((double)-1);
					else
						greeks_diff.add(Math.pow(delta-d,2)+Math.pow(gamma-g,2)+Math.pow(vega-v,2));
				}

				int best_sell = find_best_sell(greeks_diff, price, bid);
				int best_buy = find_best_buy(greeks_diff, price, ask);


				
				double g1;
				double g2;
				
				if(best_sell != -1)
					g1 = bid - price.get(best_sell)[0];
				else
					g1 = 0;

				
				if(best_buy != -1)
					g2 = price.get(best_buy)[0] - ask;
				else
					g2 = 0;
		
				int quantity = 1;
					
				if (best_sell == -1 && best_buy == -1){
					orders.add(new OrderInfo(Usymbol, OrderSide.BUY, price.get(0)[0], 0));
					continue;
				} else if (best_sell == -1 || owned_options.get(symbol) < quantity){
					log("Placing order to buy " + quantity + " at " + price.get(best_buy)[0]);
					orders.add(new OrderInfo(Usymbol, OrderSide.BUY, price.get(best_buy)[0], quantity));
					owned_options.put(symbol, owned_options.get(symbol) + quantity);
		
				} else if (best_buy == -1 && owned_options.get(symbol) >= quantity){
					log("Placing order to sell " + quantity + " at " + price.get(best_sell)[0]);
					orders.add(new OrderInfo(Usymbol, OrderSide.BUY, price.get(best_sell)[0], -1*quantity));
					owned_options.put(symbol, owned_options.get(symbol) - quantity);
				} else {
					if(g2 > g1){
						log("Placing order to buy " + quantity + " at " + price.get(best_buy)[0]);
						orders.add(new OrderInfo(Usymbol, OrderSide.BUY, price.get(best_buy)[0], quantity));
						owned_options.put(symbol, owned_options.get(symbol) + quantity);
					} else {
						log("Placing order to sell " + quantity + " at " + price.get(best_sell)[0]);
						orders.add(new OrderInfo(Usymbol, OrderSide.BUY, price.get(best_sell)[0], -1*quantity));
						owned_options.put(symbol, owned_options.get(symbol) - quantity);
					}
				}

				
			}
		}
		log("Size = " + Integer.toString(orders.size()));
		//log(Integer.toString(orders.toArray(new OrderInfo[orders.size()]).length);
		/*
		if(orders.size() > 0){
			OrderInfo[] new_orders = new OrderInfo[1];
			new_orders[0] = orders.get(0);
			return new_orders;
		}
		else
			return new OrderInfo[0];
		*/
		if(orders.size() > 0){log("passing something");
			return orders.toArray(new OrderInfo[orders.size()]);
		} else {log("passing nothing");
			return orders.toArray(new OrderInfo[orders.size()]);
			//return new OrderInfo[0];
		}
	}

	public void orderFilled(String idSymbol, double price, int quantity)
	{
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
		cash += quantity * price;
		log(Double.toString(cash));
		return;
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}
	
	public OptionsCase getOptionCaseImplementation() {
		return this;
	}
	
	public class _Gaussian {

	    // return phi(x) = standard Gaussian pdf
	    public double phi(double x) {
	        return Math.exp(-x*x / 2) / Math.sqrt(2 * Math.PI);
	    }

	    // return phi(x, mu, signma) = Gaussian pdf with mean mu and stddev sigma
	    public double phi(double x, double mu, double sigma) {
	        return phi((x - mu) / sigma) / sigma;
	    }

	    // return Phi(z) = standard Gaussian cdf using Taylor approximation
	    public double Phi(double z) {
	        if (z < -8.0) return 0.0;
	        if (z >  8.0) return 1.0;
	        double sum = 0.0, term = z;
	        for (int i = 3; sum + term != sum; i += 2) {
	            sum  = sum + term;
	            term = term * z * z / i;
	        }
	        return 0.5 + sum * phi(z);
	    }

	    // return Phi(z, mu, sigma) = Gaussian cdf with mean mu and stddev sigma
	    public double Phi(double z, double mu, double sigma) {
	        return Phi((z - mu) / sigma);
	    } 

	    // Compute z such that Phi(z) = y via bisection search
	    public double PhiInverse(double y) {
	        return PhiInverse(y, .00000001, -8, 8);
	    } 

	    // bisection search
	    private double PhiInverse(double y, double delta, double lo, double hi) {
	        double mid = lo + (hi - lo) / 2;
	        if (hi - lo < delta) return mid;
	        if (Phi(mid) > y) return PhiInverse(y, delta, lo, mid);
	        else              return PhiInverse(y, delta, mid, hi);
	    }


	}

}