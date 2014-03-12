

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

/*
 * This is a barebones sample of a OptionCase implementation.  This sample is "working", however.
 * This means that you can launch Freeway, upload this job, and run it against the provided sample data.
 * 
 * Your team will need to provide your own implementation of this case.
 */

public class MIT3OptionsCase extends AbstractOptionsCase implements OptionsCase {
		
	private IDB myDatabase;
	int factor;
	int timeStep = 0;
	int trivialarb = 0;
	double greekLimits[][] = new double[2][3]; // min/max, delta/gamma/vega
	double greeks[] = new double[3];
	double goalGreeks[] = new double[3];
	double[] greekForecast = new double[3];
	double vol;
	double pnl;
	int run;
	
	double[][][] optionPrices = new double[5][2][3]; // strike, 100/130, bid/ask/fair
	double[] underlyingPrice = new double[3]; // bid/ask/fair
	String[][] optionTickers = new String[5][2]; // strike, 100/130
	String underlyingTicker;
	
	
	
	int[][] optionPositions = new int[5][2]; // strike, 100/130
	int underlyingPosition;
	boolean runAway = false;
	
	double cashPosition;

	public void addVariables(IJobSetup setup) {
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
		setup.addVariable("runMe", "1 if want to run, 0 otherwise", "int", "1");
	}

	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
		myDatabase = database;
		timeStep = 0;
		for (int i = 0; i < 3; i++)
		{
			greekLimits[0][i] = -100.0;
			greekLimits[1][i] = 100.0;
		}
		vol = 0.0;
		// helper method for accessing declared variables
		factor = getIntVar("someFactor"); 
		run = getIntVar("runMe");
		for (int i = 0; i < 5; i++)
			optionPositions[i][0] = optionPositions[i][1] = 0;
		underlyingPosition = 0;
		cashPosition = 0.0;
		
		for (int i = 0; i < 3; i++)
			greekForecast[i] = 0.0;
		trivialarb = 0;
		runAway = false;
	}

	public void newBidAsk(String idSymbol, double bid, double ask) {
//		knownSymbols.add(idSymbol);
//		log("I received a new bid of " + bid + ", and ask of " + ask + ", for " + idSymbol);
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		double strike = details.strikePrice;
		
		if (strike > 70.0) // not the underlying
		{
			int ind = idSymbol.indexOf('5');
			int june = 0;
			if (ind == -1)
				june = 1;
			int s = (int) (Math.round(strike) + .01);
			
			optionTickers[(s - 80) / 10][june] = idSymbol;
			optionPrices[(s - 80) / 10][june][0] = bid;
			optionPrices[(s - 80) / 10][june][1] = ask;
			optionPrices[(s - 80) / 10][june][2] = (bid + ask) / 2;
			
			//log("Strike: " + s + " Symbol: " + idSymbol + " Month: " + (june == 0 ? "May" : "June") + " Fair: " + optionPrices[(s - 80) / 10][june][2]);
		}
		else
		{
			underlyingTicker = idSymbol;
			underlyingPrice[0] = bid;
			underlyingPrice[1] = ask;
			underlyingPrice[2] = (bid + ask) / 2;
			log("Ticker: " + underlyingTicker + " Fair: " + underlyingPrice[2]);
		}
		
		
	}

	public void orderFilled(int volume, double fillPrice) {
		log("ORDERFILLED SHOULDN'T BE CALLED HERE");
//		log("My order was filled with qty of " + volume + " at a price of " + fillPrice);
	}

	
	
	public void newRiskMessage(RiskMessage msg) {
//		runAway = true;
		
		greekLimits[0][0] = msg.minDelta;
		greekLimits[0][1] = msg.minGamma;
		greekLimits[0][2] = msg.minVega;
		
		greekLimits[1][0] = msg.maxDelta;
		greekLimits[1][1] = msg.maxGamma;
		greekLimits[1][2] = msg.maxVega;
		
		log("New risk limits");
		log("Min delta: " + greekLimits[0][0]);
		log("Min gamma: " + greekLimits[0][1]);
		log("Min vega: " + greekLimits[0][2]);
		log("Max delta: " + greekLimits[1][0]);
		log("Max gamma: " + greekLimits[1][1]);
		log("Max vega: " + greekLimits[1][2]);
		
		for (int i = 0; i < 3; i++)
		{
			double lo = 0.99 * greekLimits[0][i] + 0.01 * greekLimits[1][i];
			double hi = 0.01 * greekLimits[0][i] + 0.99 * greekLimits[1][i];
			
			greekLimits[0][i] = lo;
			greekLimits[1][i] = hi;
		}
	}

	public void newForecastMessage(ForecastMessage msg) {
		greekForecast[0] = msg.delta;
		greekForecast[1] = msg.gamma;
		greekForecast[2] = msg.vega;
		
//		log("I received a forecast message!");
	}
	
	public void newVolUpdate(VolUpdate msg) {
		vol = msg.impliedVol;
		timeStep++;
//		log("New vol: " + vol);
	}
	
	public void penaltyFill(String idSymbol, double price, int quantity) {
		log("Penalty called...oh no!");
		for (int i = 0; i < 5; i++)
			for (int june = 0; june < 2; june++)
			{
				log("Strike: " + (80 + 10 * i) + ((june == 0) ? " May" : " June") + optionPositions[i][june]);
				
			}
		log(idSymbol + " " + price + " " + quantity);
		orderFilled(idSymbol, price, quantity);
	}
	
	public double volArb(int strike, int june, int buy, double edge)
	{
		double fair = Optionsutil.Call(underlyingPrice[2], (double) strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol);
		
		if (buy == 1 && fair > edge + optionPrices[(strike - 80) / 10][june][1]) // want to buy, and fair price is higher than the ask
		{
//			log("Want to buy strike " + strike + " " + (june == 1 ? "June" : "May") + ". Fair is " + fair + ", can buy for " + optionPrices[(strike-80)/10][june][1]);
			return fair - edge - optionPrices[(strike - 80) / 10][june][1];
		}
		if (buy == 0 && fair + edge < optionPrices[(strike - 80) / 10][june][0]) // want to sell, and fair price is lower than the bid
		{
//			log("Want to sell strike " + strike + " " + (june == 1 ? "June" : "May") + ". Fair is " + fair + ", can sell for " + optionPrices[(strike-80)/10][june][0]);
			return optionPrices[(strike - 80) / 10][june][0] - fair - edge;
		}
		return 0.0;
	}
	
	public boolean trivialArb(int strike, int june, double edge)
	{
		if (optionPrices[(strike - 70) / 10][june][0] - optionPrices[(strike - 80) / 10][june][1] > 10 + edge)
			return true;
		return false;
	}
	
	public String getSymbol(int strike, int june)
	{
		if (strike == 0)
			return underlyingTicker;
		return optionTickers[(strike - 80) / 10][june];
	}

	public OrderInfo[] placeOrders() {
		if (run == 1)
		{
		ArrayList<OrderInfo> orderList = new ArrayList<OrderInfo>();
		
		pnl = 0.0;
		greeks[0] = greeks[1] = greeks[2] = 0;
		for (int i = 0; i < 5; i++)
			for (int june = 0; june < 2; june++)
			{
				pnl += optionPositions[i][june] * optionPrices[i][june][2];
				greeks[0] += optionPositions[i][june] * Optionsutil.calculateDelta(underlyingPrice[2], 80 + i * 10, (100 + 30 * june - timeStep) / 365.0, .01, vol);
				greeks[1] += optionPositions[i][june] * Optionsutil.calculateGamma(underlyingPrice[2], 80 + i * 10, (100 + 30 * june - timeStep) / 365.0, .01, vol);
				greeks[2] += optionPositions[i][june] * Optionsutil.calculateVega(underlyingPrice[2], 80 + i * 10, (100 + 30 * june - timeStep) / 365.0, .01, vol);
			}
		greeks[0] += underlyingPosition;
		
		if (runAway)
		{
			runAway = false;
			if (
				(greekLimits[0][1] > greeks[1] && greekLimits[1][2] < greeks[2]) ||
				(greekLimits[1][1] < greeks[1] && greekLimits[0][2] > greeks[2]))
			{
				log("runAway entered");
				for (int i = 0; i < 5; i++)
					for (int june = 0; june < 2; june++)
					{
						if (optionPositions[i][june] > 0)
						{
							orderList.add(new OrderInfo(getSymbol(80 + i * 10, june), OrderSide.SELL, 0.01, optionPositions[i][june]));
						}
						if (optionPositions[i][june] < 0)
						{
							orderList.add(new OrderInfo(getSymbol(80 + i * 10, june), OrderSide.BUY, 1000, -optionPositions[i][june]));
						}
						
					}
				if (underlyingPosition > 0)
				{
					orderList.add(new OrderInfo(getSymbol(0, 0), OrderSide.SELL, 0.01, underlyingPosition));
				}
				if (underlyingPosition < 0)
				{
					orderList.add(new OrderInfo(getSymbol(0, 0), OrderSide.BUY, 1000, -underlyingPosition));
				}
			}
			else
			{
				double fgamma = greeks[1], fvega = greeks[2];
				int buygammai = 0, buygammajune = 0;
				int sellgammai = 0, sellgammajune = 0;
				double best_buy = 1000000000.0;
				double best_sell = 1000000000.0;
				
				for (int i = 0; i < 5; i++)
					for (int june = 0; june < 2; june++)
					{
						double buy_price = (optionPrices[i][june][1] - Optionsutil.Call(underlyingPrice[2],  80 + i * 10,  (100 + 30 * june - timeStep) / 365.0, .01, vol))/ Optionsutil.calculateGamma(underlyingPrice[2], 80 + i * 10, (100 + 30 * june - timeStep) / 365.0, .01, vol);
						double sell_price = (Optionsutil.Call(underlyingPrice[2],  80+i*10,  (100+30*june-timeStep)/365.0,  .01,  vol) - optionPrices[i][june][0]) / Optionsutil.calculateGamma(underlyingPrice[2], 80+i*10, (100+30*june-timeStep)/365.0, .01, vol);
						
						if (buy_price < best_buy)
						{
							best_buy = buy_price;
							buygammai = i;
							buygammajune = june;
						}
						
						if (sell_price < best_sell)
						{
							best_sell = sell_price;
							sellgammai = i;
							sellgammajune = june;
						}
					}
				
				int buyq = 0, sellq = 0;
				while (fgamma < greekLimits[0][1] || fvega < greekLimits[0][2])
				{
					buyq++;
					fgamma += Optionsutil.calculateGamma(underlyingPrice[2],  80 + buygammai * 10,  (100 + 30 * buygammajune - timeStep) / 365.0,  .01,  vol);
					fvega += Optionsutil.calculateVega(underlyingPrice[2], 80 + buygammai *10, (100 + 30 * buygammajune - timeStep) / 365.0, .01, vol);
				}
				
				while (fgamma > greekLimits[1][1] || fvega > greekLimits[1][2])
				{
					sellq++;
					fgamma -= Optionsutil.calculateGamma(underlyingPrice[2], 80+sellgammai*10,(100+30*sellgammajune-timeStep)/365.0,.01,vol);
					fvega -= Optionsutil.calculateVega(underlyingPrice[2], 80+sellgammai*10,(100+30*sellgammajune-timeStep)/365.0,.01,vol);
				}
				
				orderList.add(new OrderInfo(getSymbol(80+buygammai*10, buygammajune), OrderSide.BUY, 0.01, buyq));
				orderList.add(new OrderInfo(getSymbol(80+sellgammai*10,sellgammajune), OrderSide.SELL, 1000.0, sellq));
				
			}
			
			
		}
		else
		{
		
		
		log("Current time: " + timeStep);
//		log("Placing orders");
		
		
		
		pnl += underlyingPosition * underlyingPrice[2];
		pnl += cashPosition;
		
		log("Delta: " + greeks[0]);
		log("Gamma: " + greeks[1]);
		log("Vega: " + greeks[2]);
		log("PnL: " + pnl);
		
//		double g_lo = 0.8 * greekLimits[0][1] + 0.2 * greekLimits[1][1];
//		double g_hi = 0.2 * greekLimits[0][1] + 0.8 * greekLimits[1][1];
//		double g_target;
//		if (greekForecast[1] > g_hi)
//			g_target = g_hi;
//		else if (greekForecast[1] < g_lo)
//			g_target = g_lo;
//		else
//			g_target = greekForecast[1];
//		
//		double v_lo = 0.8 * greekLimits[0][2] + 0.2 * greekLimits[1][2];
//		double v_hi = 0.2 * greekLimits[0][2] + 0.8 * greekLimits[1][2];
//		double v_target;
//		if (greekForecast[2] > v_hi)
//			v_target = v_hi;
//		else if (greekForecast[2] < v_lo)
//			v_target = v_lo;
//		else
//			v_target = greekForecast[2];
//		
//		double dg = g_target - greeks[1];
//		double dv = v_target - greeks[2];
//		
//		// want to get +dg gamma, +dv vega
//		
//		int bi1 = -1, bjune1 = -1, bi2 = -1, bjune2 = -1;
//		double bpl = -100000000.0;
//		
//		boolean hit = false;
		
//		double pl = 0.0;
//		for (int i1 = 0; i1 < 5; i1++)
//			for (int june1 = 0; june1 < 2; june1++)
//				for (int i2 = i1 + 1; i2 < 5; i2++)
//					for (int june2 = 0; june2 < 2; june2++)
//					{
//						double g1 = Optionsutil.calculateGamma(underlyingPrice[2],  80 + i1 * 10,  (100 + 30 * june1 - timeStep) / 365.0, .01, vol);
//						double g2 = Optionsutil.calculateGamma(underlyingPrice[2],  80 + i2 * 10,  (100 + 30 * june2 - timeStep) / 365.0, .01,  vol);
//						double v1 = Optionsutil.calculateVega(underlyingPrice[2], 80 + i1 * 10, (100 + 30 * june1 - timeStep) / 365.0, .01, vol);
//						double v2 = Optionsutil.calculateVega(underlyingPrice[2], 80 + i2 * 10, (100 + 30 * june2 - timeStep) / 365.0, .01, vol);
////						log("GOT HERE1");
//						if (!hit && (g2 * v1 - g1 * v2 > 0.05 || g2 * v1 - g1 * v2 < -0.05) && (g1 * v2 - g2 * v1 > 0.05 || g1 * v2 - g2 * v1 < -0.05))
//						{
//							hit = true;
//							log("GOT HERE");
//							int q2 = (int) ((dg * v1 - dv * g1) / (g2 * v1 - g1 * v2));
//							int q1 = (int) ((dg * v2 - dv * g2) / (g1 * v2 - g2 * v1));
//							
//							
//							double fair1 = Optionsutil.Call(underlyingPrice[2],  80 + i1 * 10,  (100 + 30 * june1 - timeStep) / 365.0,  .01,  vol);
//							double fair2 = Optionsutil.Call(underlyingPrice[2], 80 + i2 * 10, (100 + 30 * june2 - timeStep) / 365.0, .01, vol);
//							if (q1 > 0) // want to buy
//							{
//								double buyPrice1 = optionPrices[i1][june1][1];
//								orderList.add(new OrderInfo(getSymbol(80 + i1 * 10, june1), OrderSide.BUY, buyPrice1, q1));
//								pl += (fair1 - buyPrice1) * q1;
//							}
//							else
//							{
//								double sellPrice1 = optionPrices[i1][june1][0];
//								orderList.add(new OrderInfo(getSymbol(80 + i1 * 10, june1), OrderSide.SELL, sellPrice1, -q1));
//								pl += (sellPrice1 - fair1) * (-q1);
//							}
//							
//							if (q2 > 0) // want to buy
//							{
//								double buyPrice2 = optionPrices[i2][june2][1];
//								pl += (fair2 - buyPrice2) * q2;
//								orderList.add(new OrderInfo(getSymbol(80 + i2 * 10, june2), OrderSide.BUY, buyPrice2, q2));
//							}
//							else
//							{
//								double sellPrice2 = optionPrices[i2][june2][0];
//								pl += (sellPrice2 - fair2) * (-q2);
//								orderList.add(new OrderInfo(getSymbol(80 + i2 * 10, june2), OrderSide.SELL, sellPrice2, -q2));
//							}
//							
////							if (pl > bpl)
////							{
////								bi1 = i1;
////								bjune1 = june1;
////								bi2 = i2;
////								bjune2 = june2;
////							}
//							
//						}
//							
//					}
//		
//		if (hit)
//		{
//			log("HEDGING PNL: " + pl);
//		}
//		else
//		{
//			log("HEDGING FAILED");
//		}
		
		
//		if (bpl > -10000.0 && bi1 != -1)
//		{
//			double g1 = Optionsutil.calculateGamma(underlyingPrice[2],  80 + bi1 * 10,  (100 + 30 * bjune1 - timeStep) / 365.0, .01, vol);
//			double g2 = Optionsutil.calculateGamma(underlyingPrice[2],  80 + bi2 * 10,  (100 + 30 * bjune2 - timeStep) / 365.0, .01,  vol);
//			double v1 = Optionsutil.calculateVega(underlyingPrice[2], 80 + bi1 * 10, (100 + 30 * bjune1 - timeStep) / 365.0, .01, vol);
//			double v2 = Optionsutil.calculateVega(underlyingPrice[2], 80 + bi2 * 10, (100 + 30 * bjune2 - timeStep) / 365.0, .01, vol);
//			
//			int q1 = (int) ((dg * v2 - dv * g2) / (g1 * v2 - g2 * v1));
//			int q2 = (int) ((dg * v1 - dv * g1) / (g2 * v1 - g1 * v2));
//			
//			if (q1 > 0) // want to buy
//			{
//				orderList.add(new OrderInfo(getSymbol(80 + bi1 * 10, bjune1), OrderSide.BUY, optionPrices[bi1][bjune1][1], q1));
//			}
//			else
//			{
//				orderList.add(new OrderInfo(getSymbol(80 + bi1 * 10, bjune1), OrderSide.SELL, optionPrices[bi1][bjune1][0], -q1));
//			}
//			
//			if (q2 > 0) // want to buy
//			{
//				orderList.add(new OrderInfo(getSymbol(80 + bi2 * 10, bjune2), OrderSide.BUY, optionPrices[bi2][bjune2][1], q2));
//			}
//			else
//			{
//				orderList.add(new OrderInfo(getSymbol(80 + bi2 * 10, bjune2), OrderSide.SELL, optionPrices[bi2][bjune2][0], -q2));
//			}
//			log("HEDGE SUCCESS: " + bpl);
//		}
//		else
//		{
//			log("HEDGING FAILED");
//		}
		
		double edge = 0.05;
		
		

		if (greeks[0] - greekForecast[0] > 2)
		{
			orderList.add(new OrderInfo(getSymbol(0, 0), OrderSide.SELL, underlyingPrice[0], (int) (greeks[0] - greekForecast[0])));
		}
		if (greeks[0] - greekForecast[0] < -2)
		{
			orderList.add(new OrderInfo(getSymbol(0, 0), OrderSide.BUY, underlyingPrice[1], (int) (greekForecast[0] - greeks[0])));
		}
		
		
		
		for (int strike = 80; strike <= 120; strike += 10)
			for (int june = 0; june <= 1; june++)
			{
				int buy = 1;
				if (volArb(strike, june, buy, edge) > 0.01 
						&& Optionsutil.calculateGamma(underlyingPrice[2],  strike,  (100 + 30 * june - timeStep) / 365.0, 0.01,  vol) + greeks[1] < greekLimits[1][1]
						&& Optionsutil.calculateVega(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol) + greeks[2] < greekLimits[1][2])
				{
					log(getSymbol(strike, june) + " BUY");
					orderList.add(new OrderInfo(getSymbol(strike, june), OrderSide.BUY, optionPrices[(strike - 80) / 10][june][1], 1));
					greeks[1] += Optionsutil.calculateGamma(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol);
					greeks[2] += Optionsutil.calculateVega(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol);
				}
				
				buy = 0;
				if (volArb(strike, june, buy, edge) > 0.01
						&& greeks[1] - Optionsutil.calculateGamma(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol) > greekLimits[0][1]
						&& greeks[2] - Optionsutil.calculateVega(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol) > greekLimits[0][2])
				{
					log(getSymbol(strike, june) + " SELL");
					greeks[1] -= Optionsutil.calculateGamma(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol);
					greeks[2] -= Optionsutil.calculateVega(underlyingPrice[2], strike, (100 + 30 * june - timeStep) / 365.0, 0.01, vol);
					orderList.add(new OrderInfo(getSymbol(strike, june), OrderSide.SELL, optionPrices[(strike - 80) / 10][june][0], 1));
				}
				
			}
		
//		for (int strike = 80; strike <= 110; strike += 10)
//			for (int june = 0; june <= 1; june++)
//				if (trivialArb(strike, june, edge))
//				{
//					orderList.add(new OrderInfo(getSymbol(strike, june), OrderSide.BUY, optionPrices[(strike - 80) / 10][june][1], 5));
//					orderList.add(new OrderInfo(getSymbol(strike + 10, june), OrderSide.SELL, optionPrices[(strike - 70) / 10][june][0], 5));
//					log("trivial ARB");
//					trivialarb++;
//					log(trivialarb + " trivial arbs");
//				}
//		
		}
		OrderInfo[] orders = orderList.toArray(new OrderInfo[orderList.size()]);
		return orders;
		}
		return new OrderInfo[0];
	}

	public void orderFilled(String idSymbol, double price, int quantity) {
		
//		log("My order for " + idSymbol + " got filled at " + price + " with quantity of " + quantity);
		
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		double strike = details.strikePrice;
		
		if (strike > 70.0) // not the underlying
		{
			int ind = idSymbol.indexOf('5');
			int june = 0;
			if (ind == -1)
				june = 1;
			int s = (int) (Math.round(strike) + 0.01);
			
			optionPositions[(s - 80) / 10][june] += quantity;
			cashPosition -= quantity * price;
		}
		else
		{
			underlyingPosition += quantity;
			cashPosition -= quantity * price;
		}

	}


	public OptionsCase getOptionCaseImplementation() {
		return this;
	}

}
