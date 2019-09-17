package eportfolium.com.karuta.model.exception;

import eportfolium.com.karuta.util.MessageUtil;

@SuppressWarnings("serial")
//@ApplicationException(rollback = true)
public class DuplicatePrimaryOrAlternateKeyException extends BusinessException {
	static public final int INFORMATIONLEVEL_TABLE_NAME = 1;

	private int informationLevel = INFORMATIONLEVEL_TABLE_NAME;
	private String tableName;

	/**
	 * This exception is thrown by an IPersistenceExceptionInterpreter when an attempt to create an entity in Derby has
	 * failed because it already exists or a unique key violation occurs. Derby cannot differentiate between the 2
	 * cases. This exception is not possible when the entity definition specifies auto-generated ids.
	 * 
	 * @param tableName
	 */
	public DuplicatePrimaryOrAlternateKeyException(String tableName) {

		// Don't convert the message ids to messages yet because we're in the
		// server's locale, not the user's.

		super();
		this.informationLevel = INFORMATIONLEVEL_TABLE_NAME;
		this.tableName = tableName;
	}

	@Override
	public String getMessage() {
		String msg;
		Object[] msgArgs;

		// We deferred converting the message ids to messages until now, when we
		// are more likely to be in the user's locale.

		msgArgs = new Object[] { tableName };
		msg = MessageUtil.toText("DuplicatePrimaryOrAlternateKeyException", msgArgs);

		return msg;
	}

	public String getTableName() {
		return tableName;
	}

	public int getInformationLevel() {
		return informationLevel;
	}
}
