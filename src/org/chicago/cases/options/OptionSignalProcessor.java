package org.chicago.cases.options;

import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.OrderRequestMessage;
import org.chicago.cases.options.OptionSignals.ProcessPenaltyRequest;
import org.chicago.cases.options.OptionSignals.RiskMessage;
import org.chicago.cases.options.OptionSignals.VolUpdate;

import com.optionscity.freeway.api.messages.Signal;
import com.optionscity.freeway.api.services.IPlaybackService.ISignalProcessor;

public class OptionSignalProcessor implements ISignalProcessor {

	/*
	 * We'll only be importing signals, so the serialization method is not needed.
	 */
	public String asString(Signal signalString) {return "";}

	@Override
	public Signal fromString(String signalString) {
		String[] parts = signalString.split(";");
		Signal signal = null;
		String msgType = parts[0];
		if (msgType.equalsIgnoreCase("Vol")) {
			double impliedVol = Double.parseDouble(parts[1]);
			signal = new VolUpdate(impliedVol);
		}
		else if (msgType.equalsIgnoreCase("Forecast")) {
			double delta = Double.parseDouble(parts[1]);
			double gamma = Double.parseDouble(parts[2]);
			double vega = Double.parseDouble(parts[3]);
			signal = new ForecastMessage(delta, gamma, vega);
		}
		else if (msgType.equalsIgnoreCase("Risk")) {
			double minDelta = Double.parseDouble(parts[1]);
			double maxDelta = Double.parseDouble(parts[2]);
			double minGamma = Double.parseDouble(parts[3]);
			double maxGamma = Double.parseDouble(parts[4]);
			double minVega = Double.parseDouble(parts[5]);
			double maxVega = Double.parseDouble(parts[6]);
			signal = new RiskMessage(minDelta, maxDelta, minGamma, maxGamma, minVega, maxVega);
		}
		else if (msgType.equalsIgnoreCase("Orders")) {
			signal = new OrderRequestMessage();
		}
		else if (msgType.equalsIgnoreCase("Penalty")) {
			double underlyingPrice = (parts.length < 2 || parts[1].isEmpty()) ? Double.MIN_VALUE : Double.parseDouble(parts[1]);
			signal = new ProcessPenaltyRequest(underlyingPrice);
		}
		else {
			throw new IllegalStateException("Signal string does not follow the required format");
		}
		return signal;
	}

}
