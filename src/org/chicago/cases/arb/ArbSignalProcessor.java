package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.arb.ArbSignals.TopOfBookUpdate;

import com.optionscity.freeway.api.messages.Signal;
import com.optionscity.freeway.api.services.IPlaybackService.ISignalProcessor;

public class ArbSignalProcessor implements ISignalProcessor {

	/*
	 * We'll only be importing signals, so the serialization method is not needed.
	 */
	public String asString(Signal signalString) {return "";}

	@Override
	public Signal fromString(String signalString) {
		String[] parts = signalString.split(";");
		if (parts.length < 1)
			throw new IllegalStateException("Invalid number of fields in signal String");
		if (parts[0].equal("ORDER")){
			// 1 = Customer Buy, 2 = Customer Sell
			CustomerSide side = Int.parseInt(parts[1]);
			double price = Double.parseDouble(parts[2]);
			return new CustomerOrder(side,price);
		}else if (parts[0].equal("BOOKUPDATE")){
			double robotBid = Double.parseDouble(parts[1]);
			double robotAsk = Double.parseDouble(parts[2]);
			Quote robotQuote = new Quote(Exchange.ROBOT, robotBid, Integer.MAX_VALUE, robotAsk, Integer.MAX_VALUE);
			double snowBid = Double.parseDouble(parts[4]);
			double snowAsk = Double.parseDouble(parts[5]);
			Quote snowQuote = new Quote(Exchange.SNOW, snowBid, Integer.MAX_VALUE, snowAsk, Integer.MAX_VALUE);
			return new TopOfBookUpdate(snowQuote, robotQuote);
		}
	}

}
