import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chicago.cases.CommonSignalProcessor;
import org.chicago.cases.options.OptionSignalProcessor;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IGrid;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;


public class SurfaceVisualizer extends AbstractJob {
	
	private static final String VOL_GRID = "VOL";
	private static final String GAMMA_GRID = "GAMMA";
	private static final String DELTA_GRID = "DELTA";
	private IGrid volGrid;
	private IGrid gammaGrid;
	private IGrid deltaGrid;
	private Map<String, double[]> priceMap = new HashMap<String, double[]>();
	private double underlyingPrice = 0;
	private double impliedVol = 0;
	protected int daysToJuneExp = 130;
	protected int daysToMayExp = 100;

	public void install(IJobSetup setup) {
		setup.setVariable("timer", "1000");
	}

	@Override
	public void begin(IContainer container) {
		super.begin(container);
		
		volGrid = container.addGrid(VOL_GRID, new String[] {"strike", "value", "month"});
		gammaGrid = container.addGrid(GAMMA_GRID, new String[] {"strike", "value", "month"});
		deltaGrid = container.addGrid(DELTA_GRID, new String[] {"strike", "value", "month"});
		
		container.subscribeToMarketBidAskMessages();
		container.subscribeToTradeMessages();
		container.subscribeToSignals();
		container.filterOnlyMyTrades(true);
		container.getPlaybackService().register(new OptionSignalProcessor());
		container.getPlaybackService().register(new CommonSignalProcessor());
		
		List<String> products = InstrumentUtilities.getSymbolsForTeamByCase(Case.OPTIONS, "MIT3");
		for (String product : products) {
			instruments().startSymbol(product);
			container.filterMarketMessages(product + ";;;;;;");
		}
	}
	
	public void onMarketBidAsk(MarketBidAskMessage msg) {
		Prices prices = instruments().getAllPrices(msg.instrumentId);
		InstrumentDetails details = instruments().getInstrumentDetails(msg.instrumentId);
		if (!details.type.isOption()) {
			underlyingPrice = (prices.bid + prices.ask) / 2.0;
		}
		else {
			priceMap.put(msg.instrumentId, new double[] {prices.bid, prices.ask});
		}
	}

	@Override
	public void onTimer() {
		if (priceMap.keySet().size() != 10 || underlyingPrice == 0) {
			log(priceMap.keySet().size() + ", " + underlyingPrice);
			return;
		}
		
		
		
		for (String idSymbol : priceMap.keySet()) {
			double[] prices = priceMap.get(idSymbol);
			double optionPrice = (prices[0] + prices[1]) / 2.0;
			InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
			boolean isMay = details.expiration.toString().toLowerCase().contains("may");
			double days = (isMay) ? daysToMayExp : daysToJuneExp;
			double gamma = Optionsutil.calculateGamma(underlyingPrice, details.strikePrice, (days / 365.0), 0.01, impliedVol);
			double delta = Optionsutil.calculateDelta(underlyingPrice, details.strikePrice, (days / 365.0), 0.01, impliedVol);
			gammaGrid.set(idSymbol, "strike", details.strikePrice);
			gammaGrid.set(idSymbol, "value", gamma);
			gammaGrid.set(idSymbol, "month", (isMay) ? 5 : 6);
			deltaGrid.set(idSymbol, "strike", details.strikePrice);
			deltaGrid.set(idSymbol, "value", delta);
			deltaGrid.set(idSymbol, "month", (isMay) ? 5 : 6);
			Date currentDate = getDate(days, idSymbol);
			double vol = theos().calculateImpliedVolatility(idSymbol, optionPrice, underlyingPrice, currentDate);
			if (Double.isNaN(vol))
				vol = 0.01;
			log(gamma + "," + vol + "," + delta);
			volGrid.set(idSymbol, "strike", details.strikePrice);
			volGrid.set(idSymbol, "value", vol);
			volGrid.set(idSymbol, "month", (isMay) ? 5 : 6);
		}
		
	}

	private Date getDate(double days, String idSymbol) {
		InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(details.expiration);
		for (int i = 0; i < days; i++) {
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
		return cal.getTime();
	}

	public void onSignal(VolUpdate msg) {
		daysToJuneExp -= 1;
		daysToMayExp -= 1;
		impliedVol = msg.impliedVol;
	}

}
