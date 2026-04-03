package org.cekpelunasan.core.entity;

import jakarta.annotation.Nullable;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
@Document(collection = "users")
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
	private AccountOfficerRoles roles;

}
