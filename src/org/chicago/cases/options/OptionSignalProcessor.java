package org.chicago.cases.options;

import org.chicago.cases.options.OptionSignals.AdminMessage;
import org.chicago.cases.options.OptionSignals.ForecastMessage;
import org.chicago.cases.options.OptionSignals.MsgType;

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
		if (parts.length < 3)
			throw new IllegalStateException("Invalid number of fields in signal String");
		Signal signal = null;
		String msgType = parts[0];
		MsgType dataPoint = MsgType.valueOf(parts[1]);
		int magnitude = Integer.parseInt(parts[2]);
		if (msgType.equalsIgnoreCase("admin")) {
			signal = new AdminMessage(dataPoint, magnitude);
		}
		else if (msgType.equalsIgnoreCase("forecast")) {
			signal = new ForecastMessage(dataPoint, magnitude);
		}
		else {
			throw new IllegalStateException("Signal string does not follow the required format");
		}
		return signal;
	}

}
