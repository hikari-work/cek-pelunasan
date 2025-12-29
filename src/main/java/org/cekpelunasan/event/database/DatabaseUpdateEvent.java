package org.cekpelunasan.event.database;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class DatabaseUpdateEvent extends ApplicationEvent {

	@Getter
	private final EventType eventType;

	@Getter
	private final boolean isSuccess;

	public DatabaseUpdateEvent(Object source, EventType eventType, boolean isSuccess) {
		super(source);
		this.eventType = eventType;
		this.isSuccess = isSuccess;
	}
}
