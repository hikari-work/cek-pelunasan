package org.cekpelunasan.entity;

/**
 * Enum representing the roles of an Account Officer.
 * <ul>
 * <li>AO: Account Officer - Can get data from the database.</li>
 * <li>PIMP: Can only get data from the database.</li>
 * <li>ADMIN: Can perform all operations.</li>
 * </ul>
 */
public enum AccountOfficerRoles {

	/**
	 * Account Officer role. Can retrieve data.
	 */
	AO,
	/**
	 * PIMP role. Can only retrieve data.
	 */
	PIMP,
	/**
	 * Administrator role. Has full access.
	 */
	ADMIN
}
