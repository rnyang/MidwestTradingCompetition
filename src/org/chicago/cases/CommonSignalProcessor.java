package org.chicago.cases;

import org.chicago.cases.CommonSignals.EndSignal;

import com.optionscity.freeway.api.messages.Signal;
import com.optionscity.freeway.api.services.IPlaybackService.ISignalProcessor;

public class CommonSignalProcessor implements ISignalProcessor {

	/*
	 * We'll only be importing signals, so the serialization method is not needed.
	 */
	public String asString(Signal signalString) {return "";}

	@Override
	public Signal fromString(String signalString) {
		String[] parts = signalString.split(";");
		Signal signal = null;
		String msgType = parts[0];
		if (msgType.equalsIgnoreCase("END")) {
			signal = new EndSignal();
		}
		return signal;
	}

}
