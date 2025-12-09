package org.cekpelunasan.event.database;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event triggered when a database update occurs.
 * <p>
 * This event carries information about the type of update and its success
 * status.
 * </p>
 */
public class DatabaseUpdateEvent extends ApplicationEvent {

	/**
	 * The type of the event.
	 */
	@Getter
	private final EventType eventType;

	/**
	 * Indicates whether the update was successful.
	 */
	@Getter
	private final boolean isSuccess;

	/**
	 * Constructs a new DatabaseUpdateEvent.
	 *
	 * @param source    the object on which the event initially occurred (never
	 *                  {@code null})
	 * @param eventType the type of the event
	 * @param isSuccess {@code true} if the update was successful, {@code false}
	 *                  otherwise
	 */
	public DatabaseUpdateEvent(Object source, EventType eventType, boolean isSuccess) {
		super(source);
		this.eventType = eventType;
		this.isSuccess = isSuccess;
	}
}
