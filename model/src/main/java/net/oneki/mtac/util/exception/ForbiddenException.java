package net.oneki.mtac.util.exception;

import net.oneki.mtac.util.exception.BusinessException;

public class ForbiddenException extends BusinessException {
	private static final long serialVersionUID = 1L;
	public static final String UNAUTHORIZED_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	public ForbiddenException() {
		this("HTTP_403", "Unauthorized");
	}

	public ForbiddenException(String message) {
		this("HTTP_403", message);
	}

	public ForbiddenException(String errorCode, String message) {
		super(errorCode, message);
	}

}
