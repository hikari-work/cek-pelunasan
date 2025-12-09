package org.cekpelunasan.event.database;

/**
 * Enum representing the types of database update events.
 */
public enum EventType {

	/**
	 * Represents a saving (Tabungan) update event.
	 */
	SAVING("Tabungan"),

	/**
	 * Represents a collection task (Kolek Tas) update event.
	 */
	KOLEK_TAS("Kolek Tas"),

	/**
	 * Represents a bill (Tagihan) update event.
	 */
	TAGIHAN("Tagihan");

	/**
	 * The human-readable value of the event type.
	 */
	public final String value;

	/**
	 * Constructs a new EventType.
	 *
	 * @param value the human-readable value
	 */
	EventType(String value) {
		this.value = value;
	}
}
