import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chicago.cases.CommonSignalProcessor;
import org.chicago.cases.AbstractOptionsCase.OptionsCase;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;
import org.chicago.cases.options.OptionSignalProcessor;
import org.chicago.cases.options.Optionsutil;
import org.chicago.cases.options.OrderInfo;
import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IGrid;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.InstrumentDetails;
import com.optionscity.freeway.api.Prices;
import com.optionscity.freeway.api.messages.MarketBidAskMessage;


public class SurfaceVisualizer extends AbstractJob {
	
	private static final String VOL_GRID = "VOL";
	private static final String GAMMA_GRID = "GAMMA";
	private IGrid volGrid;
	private IGrid gammaGrid;
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
		if (priceMap.size() != 10 || underlyingPrice == 0) {
			log(priceMap.size() + ", " + underlyingPrice);
			return;
		}
			
		
		for (String idSymbol : priceMap.keySet()) {
			InstrumentDetails details = instruments().getInstrumentDetails(idSymbol);
			boolean isMay = details.expiration.toString().toLowerCase().contains("may");
			double days = (isMay) ? daysToMayExp : daysToJuneExp;
			double gamma = Optionsutil.calculateGamma(underlyingPrice, details.strikePrice, (days / 365.0), 0.01, impliedVol);
			gammaGrid.set(idSymbol, "strike", details.strikePrice);
			gammaGrid.set(idSymbol, "value", gamma);
			gammaGrid.set(idSymbol, "month", (isMay) ? 5 : 6);
		}
		
	}

	public void onSignal(VolUpdate msg) {
		daysToJuneExp -= 1;
		daysToMayExp -= 1;
		impliedVol = msg.impliedVol;
	}

}
