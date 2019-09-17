package eportfolium.com.karuta.model.exception;


import eportfolium.com.karuta.util.MessageUtil;

/**
 * Throw this exception when an authentication exception has occurred.  This implementation allows
 * any error message to be specified, enabling different messages for "invalid loginId" and
 * "wrong password".  For greater security you may prefer to replace it with one that returns just 
 * one consistent message from getMessage(), eg. "loginId or password is incorrect".
 */
@SuppressWarnings("serial")
public class AuthenticationException extends BusinessException {
	String messageId;
	Object[] messageArgs;

	public AuthenticationException(String messageId) {
		this(messageId, null);
	}

	public AuthenticationException(String messageId, Object messageArg) {
		super();
		this.messageId = messageId;
		this.messageArgs = new Object[] { messageArg };
	}

	@Override
	public String getMessage() {

		// We deferred converting the message ids to messages until now, when we are more likely to be in the user's
		// locale.

		String msg = MessageUtil.toText(messageId, messageArgs);
		return msg;
	}

	public String getMessageId() {
		return messageId;
	}
}
