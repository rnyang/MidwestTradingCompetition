

import java.util.ArrayList;
import java.util.Collections;
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

public class CHI1Option extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();

	// forecast greeks
	private double fd;
	private double fg;
	private double fv;
	
	// risk limits
	private double rdmin;
	private double rdmax;
	private double rgmin;
	private double rgmax;
	private double rvmin;
	private double rvmax;
	
	// current risk totals
	private double rd;
	private double rg;
	private double rv;
	
	// volatility
	private double vol;
	private int time;
	
	// forecast/risk message counter 0::forecast, 1::risk
	private int[] msgc;
	
	// holdings array
	private ArrayList<Asset> holdings;
	
	private ArrayList<OrderInfo> tradestack;
	
	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		
		msgc = new int[2];
		holdings = new ArrayList<Asset>();
		tradestack = new ArrayList<OrderInfo>();
		vol = 0.0;
		time = 100;
		
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
		//knownSymbols.add(idSymbol);
		//log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		String d = details.displayExpiration;
		int exp = 0;
		if (d.contains("2014"))
		{
			d = d.substring(15,16);
			exp = Integer.parseInt(d);
			if (exp == 5)
				exp = 100;
			else if (exp == 6)
				exp = 130;
		}
		log("Strike for the symbol " + idSymbol + " is " + details.strikePrice);
		int ind = -1;
		for (int i = holdings.size(); i >= 0; i--) 
		{
			if (holdings.get(i).idString.equals(idSymbol))
			{
				ind = i;
				holdings.get(i).mmbid = bid;
				holdings.get(i).mmask = ask;
				break;
			}
		}

		int dMin = -1;
		double expt = (double)exp - (100-(double)time);
		double v = Optionsutil.Call(ask, details.strikePrice, expt/365.0, 0.01, vol);
		double baDelt = Optionsutil.calculateDelta(ask, details.strikePrice, expt/365.0, 0.01, vol);
		for (int i = holdings.size(); i >= 0; i--)
		{
			if (holdings.get(i).delta < baDelt)
			{
				dMin = i;
				break;
			}
		}
		for (int i = dMin; i >= 0; i--)
			sell(i);
		updateRisk();
		buyToRiskMax(idSymbol, ask, details.strikePrice, expt/365.0, ind);
		Collections.sort(holdings);
	}

	public Asset sell(int i)
	{
		tradestack.add(new OrderInfo(holdings.get(i).idString, OrderSide.SELL, holdings.get(i).strike, holdings.get(i).quantity));
		return holdings.remove(i);
	}
	
	public double min(double d1, double d2)
	{
		if (d1 > d2)
			return d1;
		return d2;
	}
	
	public void buyToRiskMax(String idSymbol, double ask, double str, double t, int ind)
	{
		double dMax = min(rdmax, fd) - rd;
		double gMax = min(rgmax, fg) - rg;
		double vMax = min(rvmax, fv) - rv;
		int quantity = 0;
		double delt = Optionsutil.calculateDelta(ask, str, t, 0.01, vol);
		double gamm = Optionsutil.calculateGamma(ask, str, t, 0.01, vol);
		double vega = Optionsutil.calculateVega(ask, str, t, 0.01, vol);
		while (dMax - delt > 0 && gMax - gamm > 0 && vMax - vega > 0)
		{
			dMax -= delt;
			gMax -= gamm;
			vMax -= vega;
			quantity++;
		}
		tradestack.add(new OrderInfo(idSymbol, OrderSide.BUY, str, quantity));
		if (ind == -1)
			holdings.add(new Asset(idSymbol, 0.0, str, (int)t, quantity, delt));
		else
			holdings.get(ind).quantity += quantity;
		updateRisk();
	}
	
	public void updateRisk()
	{
		rd = 0;
		rg = 0;
		rv = 0;
		for (Asset a : holdings)
		{
			rd += a.delta;
			rg += a.gamma;
			rv += a.vega;
		}
			
	}
	
	public void enforce()
	{
		for (int i = holdings.size(); i >= 0; i--)
		{
			if (holdings.get(i).delta < 0)
				sell(i);
		}
		updateRisk();
		Collections.sort(holdings, Asset.dgv);
		Asset a;
		while (min(rdmax, fd) - rd < 0 || min(rgmax, fg) - rg < 0 || min(rvmax, fv) - rv < 0)
		{
			a = sell(0);
			rd -= a.delta;
			rg -= a.gamma;
			rv -= a.vega;
		}
		Collections.sort(holdings);
	}
	
	public void orderFilled(int volume, double fillPrice) {
		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	public void newRiskMessage(RiskMessage msg) {
		log("I received an admin message!");
		rdmin = msg.minDelta;
		rdmax = msg.maxDelta;
		rgmin = msg.minGamma;
		rgmax = msg.maxGamma;
		rvmin = msg.minVega;
		rvmax = msg.maxVega;
		msgc[1]++;
		enforce();
	}

	public void newForecastMessage(ForecastMessage msg) {
		log("I received a forecast message!");	
		fd = msg.delta;
		fg = msg.gamma;
		fv = msg.vega;
		msgc[0]++;
		enforce();
	}

	public void newVolUpdate(VolUpdate msg) {
		log("I received a vol update message!");
		vol = msg.impliedVol;
		time--;
		for (int i = holdings.size(); i >= 0; i--)
		{
			holdings.get(i).tick();
			holdings.get(i).update(vol);
		}
		Collections.sort(holdings);
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
	}

	public OrderInfo[] placeOrders() {
		log("Placing orders");
		OrderInfo[] orders = (OrderInfo[])tradestack.toArray();
		tradestack.clear();
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
