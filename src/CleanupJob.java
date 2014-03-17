import java.util.Date;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.PositionRisk;


public class CleanupJob extends AbstractJob {
	
	private static IDB trades;
	private static IDB pnl;


	public void install(IJobSetup setup) {
		setup.setVariable("timer", "2500");
	}

	@Override
	public void begin(IContainer container) {
		super.begin(container);
		trades = container.getDB("trades");
		pnl = container.getDB("pnl");
	}

	@Override
	public void onTimer() {
		trades.removeAll();
		pnl.removeAll();
		//PositionRisk risk = positions().getPositionRisk();
		//log("Before execution, pnl=" + risk.dayTradeProfitAndLoss + ", positions=" + risk.dayTradePosition);
		//positions().commitPositions(new Date());
		//log("After execution, pnl=" + risk.dayTradeProfitAndLoss + ", positions=" + risk.dayTradePosition);
		container.stopJob("Cleanup complete");
	}


}
