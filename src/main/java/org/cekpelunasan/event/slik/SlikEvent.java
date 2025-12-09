package org.cekpelunasan.event.slik;

import org.springframework.context.ApplicationEvent;

/**
 * Event related to SLIK (Sistem Layanan Informasi Keuangan) operations.
 */
public class SlikEvent extends ApplicationEvent {

	/**
	 * Constructs a new SlikEvent.
	 *
	 * @param source the object on which the event initially occurred (never
	 *               {@code null})
	 */
	public SlikEvent(Object source) {
		super(source);
	}
}
