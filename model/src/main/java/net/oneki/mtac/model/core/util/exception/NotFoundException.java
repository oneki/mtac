package net.oneki.mtac.model.core.util.exception;

import net.oneki.mtac.model.core.util.exception.BusinessException;

public class NotFoundException extends BusinessException {
	private static final long serialVersionUID = 1L;

	public NotFoundException() {
		this("HTTP_404", "Not found");
	}

	public NotFoundException(String message) {
		this("HTTP_404", message);
	}

	public NotFoundException(String errorCode, String message) {
		super(errorCode, message);
	}

}
