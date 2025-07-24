package net.oneki.mtac.model.core.util.exception;

public class UnauthenticatedException extends BusinessException {
	private static final long serialVersionUID = 1L;

	public UnauthenticatedException() {
		this("HTTP_401", "Unauthenticated");
	}

	public UnauthenticatedException(String message) {
		this("HTTP_401", message);
	}

	public UnauthenticatedException(String errorCode, String message) {
		super(errorCode, message);
	}

}
