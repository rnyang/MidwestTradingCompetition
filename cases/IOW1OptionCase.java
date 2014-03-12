import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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

public class IOW1OptionCase extends AbstractOptionsCase implements OptionsCase {

	private IDB myDatabase;
	int factor;
	private List<String> knownSymbols = new ArrayList<String>();
	HashMap<String, Asset> assets = new HashMap<String, Asset>();
	Asset underlying;

	static double totalPurchased = 0;
	static double totalSold = 0;

	double civ = 0;

	GreekTriplet forecast = new GreekTriplet(0, 0, 0);
	GreekTriplet riskHi = new GreekTriplet(5, 100, 2000);
	GreekTriplet riskLo = new GreekTriplet(5, -100, -2000);

	int currentTick = 0;

	class Asset {
		InstrumentDetails details;
		int numShares;
		double avgPrice;
		LinkedList<BidAsk> historicPricing;
		GreekTriplet currentGreeks;
		int ticks;
		int assetClass;
		double fairValue = 0;
		String symbol;

		Asset(InstrumentDetails details, String idSymbol) {
			this.details = details;
			if (details.strikePrice == 0) {
				underlying = this;
				assetClass = 0;
				ticks = 130;
			} else if (idSymbol.contains("0627")) {
				assetClass = 2;
				ticks = 130;
			} else {
				assetClass = 1;
				ticks = 100;
			}
			symbol = idSymbol;
			historicPricing = new LinkedList<BidAsk>();
			numShares = 0;
			avgPrice = 0;
			currentGreeks = new GreekTriplet(0, 0, 0);
		}

		void newTransaction(double price, double vol) {
			if (vol < 0) {
				totalSold += vol * price;
				numShares += vol;
			} else {
				totalPurchased += vol * price;
				avgPrice = (vol * price + avgPrice * numShares) / (numShares += vol);
			}
		}

		void newBidAsk(double bid, double ask) {
			historicPricing.push(new BidAsk(bid, ask));
		}

		void updateGreek() {
			double spot = underlying.historicPricing.getFirst().median();
			double strike = details.strikePrice;
			double t = (ticks - currentTick) / 365.0;
			double rate = .01;
			double vol = civ;
			double gamma = Optionsutil.calculateGamma(spot, strike, t, rate, vol);
			double delta = Optionsutil.calculateDelta(spot, strike, t, rate, vol);
			double vega = Optionsutil.calculateVega(spot, strike, t, rate, vol);
			currentGreeks = new GreekTriplet(gamma, delta, vega);
			fairValue = Optionsutil.Call(spot, strike, t, rate, vol);
		}

		class BidAsk {
			double bid;
			double ask;

			BidAsk(double bid, double ask) {
				this.bid = bid;
				this.ask = ask;
			}

			double median() {
				return (bid + ask) / 2;
			}

		}
	}

	static class GreekTriplet {
		double[] greeks = new double[3];
		String parity;

		GreekTriplet(double gamma, double delta, double vega) {
			greeks[0] = gamma;
			greeks[1] = delta;
			greeks[2] = vega;
		}

		GreekTriplet(double[] greeks) {
			this.greeks = greeks;
		}

		public String toString() {
			return "Gamma: " + greeks[0] + " Delta: " + greeks[1] + " Vega: " + greeks[2];
		}

		double getGamma() {
			return greeks[0];
		}

		double getVega() {
			return greeks[2];
		}

		double getDelta() {
			return greeks[1];
		}

		GreekTriplet sub(GreekTriplet other) {
			return new GreekTriplet(this.getGamma() - other.getGamma(), this.getDelta() - other.getDelta(), this.getVega() - other.getVega());
		}

		double compareTo(GreekTriplet other) {
			return Math.abs(this.getDelta() - other.getDelta()) + Math.abs(this.getVega() - other.getVega()) + Math.abs(this.getGamma() - other.getGamma());
		}
	}

	public void addVariables(IJobSetup setup) {
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
	}

	ArrayList<Asset> buffer = new ArrayList<Asset>();

	public void newBidAsk(String idSymbol, double bid, double ask) {
		Asset t = assets.get(idSymbol);
		if (t == null) {
			t = new Asset(instruments().getInstrumentDetails(idSymbol), idSymbol);
			assets.put(idSymbol, t);
			knownSymbols.add(idSymbol);
		}
		t.newBidAsk(bid, ask);
	}

	public GreekTriplet getPortfolioGreeks() {
		double Gamma = 0;
		double Vega = 0;
		double Delta = 0;
		for (String i : knownSymbols) {
			Asset a = assets.get(i);
			a.updateGreek();
			Delta += a.numShares * a.currentGreeks.getDelta();
			Gamma += a.numShares * a.currentGreeks.getGamma();
			Vega += a.numShares * a.currentGreeks.getVega();
		}
		return new GreekTriplet(Gamma, Delta, Vega);
	}

	public void newRiskMessage(RiskMessage msg) {
		riskHi = new GreekTriplet(msg.maxGamma, msg.maxDelta, msg.maxVega);
		riskLo = new GreekTriplet(msg.minGamma, msg.minDelta, msg.minVega);
	}

	public void newForecastMessage(ForecastMessage msg) {
		forecast = new GreekTriplet(msg.gamma, msg.delta, msg.vega);
	}

	public void newVolUpdate(VolUpdate msg) {
		civ = msg.impliedVol;
	}

	public void penaltyFill(String idSymbol, double price, int quantity) {
		orderFilled(idSymbol, price, quantity);
	}

	GreekTriplet getTarget(double... buffers) {
		double[] newTargets = new double[3];
		for (int i = 0; i < 3; i++) {
			if (forecast.greeks[i] > riskHi.greeks[i]) {
				newTargets[i] = riskHi.greeks[i];
			} else if (forecast.greeks[i] < riskLo.greeks[i]) {
				newTargets[i] = riskLo.greeks[i];
			} else {
				newTargets[i] = forecast.greeks[i];
			}
			newTargets[i] = newTargets[i] - newTargets[i] * buffers[i];
		}
		return new GreekTriplet(newTargets);
	}

	boolean withinRiskParameters(GreekTriplet ach) {
		for (int i = 0; i < 3; i++) {
			if (ach.greeks[i] > riskHi.greeks[i] || ach.greeks[i] < riskLo.greeks[i])
				return false;
		}
		return true;
	}

	boolean withinTarget(double threshold, GreekTriplet ach, GreekTriplet target) {
		return (ach.getGamma() < target.getGamma() + threshold * 5 && ach.getGamma() > target.getGamma() - threshold * 5)
				&& (ach.getDelta() < target.getDelta() + threshold * 100 && ach.getDelta() > target.getDelta() - threshold * 100)
				&& (ach.getVega() < target.getVega() + threshold * 2000 && ach.getVega() > target.getVega() - threshold * 2000);
	}

	public OrderInfo[] placeOrders() {
		if (currentTick++ > 95)
			return new OrderInfo[0];

		GreekTriplet ach = getPortfolioGreeks();
		GreekTriplet target = getTarget(.1, .05, .05);

		if (withinRiskParameters(ach) && withinTarget(.1, ach, target)) {
			return new OrderInfo[0];
		}

		int frontShares = 0;
		int backShares = 0;
		int underlyingShares = 0;
		HashSet<String> purchases = new HashSet<String>();

		if (target.getVega() / target.getGamma() > 1) {

			GreekTriplet fronts = new GreekTriplet(0, 0, 0);
			GreekTriplet backs = new GreekTriplet(0, 0, 0);

			double totalHold = 0;
			for (String sym : knownSymbols) {
				Asset a = assets.get(sym);
				totalHold += a.fairValue * a.numShares;
				if (a.assetClass == 0)
					continue;
				boolean go = false;
				double p = a.details.strikePrice;
				if (p == 80) {
					if (underlying.fairValue < 90)
						go = true;
				} else if (p == 90) {
					if (underlying.fairValue < 100 && underlying.fairValue > 80)
						go = true;
				} else if (p == 100) {
					if (underlying.fairValue < 110 && underlying.fairValue > 90)
						go = true;
				} else if (p == 110) {
					if (underlying.fairValue < 120 && underlying.fairValue > 100)
						go = true;
				} else if (p == 120) {
					if (underlying.fairValue > 110)
						go = true;
				}
				if (go) {
					purchases.add(sym);
					for (int i = 0; i < 3; i++) {
						if (a.assetClass == 1) {
							fronts.greeks[i] += a.currentGreeks.greeks[i];
						} else {
							backs.greeks[i] += a.currentGreeks.greeks[i];
						}
					}
				}
			}
			backShares = (int) ((target.getVega() * fronts.getGamma() - target.getGamma() * fronts.getVega()) / (backs.getVega() * fronts.getGamma() - fronts.getVega() * backs.getGamma()));
			frontShares = (int) ((target.getGamma() / fronts.getGamma()) - ((target.getVega() * backs.getGamma() * fronts.getGamma() - target.getGamma() * fronts.getVega() * backs.getGamma()) / (backs
					.getVega() * fronts.getGamma() * fronts.getGamma() - fronts.getVega() * backs.getGamma() * fronts.getGamma())));
			underlyingShares = (int) -(fronts.getDelta() * frontShares + backs.getDelta() * backShares);
		}

		OrderInfo[] orders = new OrderInfo[11];
		int i = 0;
		GreekTriplet c = new GreekTriplet(0, 0, 0);
		for (String sym : knownSymbols) {
			Asset a = assets.get(sym);
			double bid = a.historicPricing.getFirst().bid;
			double ask = a.historicPricing.getFirst().ask;
			if (a == underlying || purchases.contains(sym)) {
				int shares;
				if (a.assetClass == 0) {
					shares = underlyingShares - a.numShares;
				} else if (a.assetClass == 1) {
					shares = frontShares - a.numShares;
				} else {
					shares = backShares - a.numShares;
				}
				for (int j = 0; j < 3; j++) {
					c.greeks[j] += a.currentGreeks.greeks[j] * shares;
				}
				orders[i++] = shares > 0 ? new OrderInfo(sym, OrderSide.BUY, ask, shares) : new OrderInfo(sym, OrderSide.SELL, bid, -1 * shares);
			} else {
				orders[i++] = a.numShares > 0 ? new OrderInfo(sym, OrderSide.SELL, bid, a.numShares) : new OrderInfo(sym, OrderSide.BUY, ask, -1 * a.numShares);
			}
		}
		return orders;
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		assets.get(idSymbol).newTransaction(price, quantity);
		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
	}

	double getPortfolioValue() {
		double t = 0;
		for (String a : knownSymbols) {
			Asset asset = assets.get(a);
			t += asset.numShares * asset.historicPricing.getFirst().ask;
		}
		return t;
	}

	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
