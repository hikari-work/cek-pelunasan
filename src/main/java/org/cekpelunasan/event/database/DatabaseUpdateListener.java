package org.cekpelunasan.event.database;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DatabaseUpdateListener {

	@EventListener(DatabaseUpdateEvent.class)
	public void onDatabaseUpdateEvent(DatabaseUpdateEvent event) {
		System.out.println("Database Update Event: " + event.toString());
	}
}
