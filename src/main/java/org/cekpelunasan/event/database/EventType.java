package org.cekpelunasan.event.database;

public enum EventType {



	SAVING("Tabungan"),
	KOLEK_TAS("Kolek Tas"),
	TAGIHAN("Tagihan");

	public final String value;
	EventType(String value) {
		this.value = value;
	}
}
