package org.cekpelunasan.event.slik;

import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Service;


public class SlikEvent extends ApplicationEvent {



	public SlikEvent(Object source) {
		super(source);
	}
}
