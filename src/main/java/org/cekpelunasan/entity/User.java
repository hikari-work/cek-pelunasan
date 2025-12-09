package org.cekpelunasan.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

/**
 * Entity representing a user of the system (likely a Telegram user).
 * <p>
 * This entity links a chat ID to user code, branch, and assigned roles.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "users")
public class User {

	/**
	 * The chat ID, serving as the primary key.
	 */
	@Id
	private Long chatId;

	/**
	 * The user code (optional).
	 */
	@Nullable
	private String userCode;

	/**
	 * The branch code (optional).
	 */
	@Nullable
	private String branch;

	/**
	 * The role assigned to the user (optional).
	 */
	@Nullable
	@Enumerated(EnumType.STRING)
	private AccountOfficerRoles roles;

}
